/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.beam.runners.flink.metrics;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import org.apache.flink.metrics.Metric;
import org.apache.flink.metrics.MetricConfig;
import org.apache.flink.metrics.MetricGroup;
import org.apache.flink.metrics.reporter.AbstractReporter;

/**
 * Flink {@link org.apache.flink.metrics.reporter.MetricReporter metrics reporter} for writing
 * metrics to a file specified via the "metrics.reporter.file.path" config key (assuming an alias of
 * "file" for this reporter in the "metrics.reporters" setting).
 */
@SuppressWarnings({
  "nullness" // TODO(https://github.com/apache/beam/issues/20497)
})
public class FileReporter extends AbstractReporter {
  @Override
  public String filterCharacters(String input) {
    return input;
  }

  private String path;
  private PrintStream ps;

  @Override
  @SuppressFBWarnings(
      "DM_DEFAULT_ENCODING") // should this method specify the encoding for the PrintStream?
  public void open(MetricConfig config) {
    synchronized (this) {
      if (path == null) {
        path = config.getString("path", null);
        log.info("Opening file: {}", path);
        if (path == null) {
          throw new IllegalStateException("FileReporter metrics config needs 'path' key");
        }
        try {
          FileOutputStream fos = new FileOutputStream(path);
          ps = new PrintStream(fos);
        } catch (FileNotFoundException e) {
          throw new IllegalStateException("FileReporter couldn't open file", e);
        }
      }
    }
  }

  @Override
  @SuppressFBWarnings("DCN_NULLPOINTER_EXCEPTION")
  public void notifyOfRemovedMetric(Metric metric, String metricName, MetricGroup group) {
    final String name = group.getMetricIdentifier(metricName, this);
    super.notifyOfRemovedMetric(metric, metricName, group);
    synchronized (this) {
      try {
        ps.printf("%s: %s%n", name, Metrics.toString(metric));
      } catch (NullPointerException e) {
        // Workaround to avoid a NPE on Flink's DeclarativeSlotManager during unregister
        // TODO Remove once FLINK-22646 is fixed on upstream Flink.
        log.warn("unable to log details on metric {}", name);
      }
    }
  }

  @Override
  public void close() {
    ps.close();
    log.info("wrote metrics to {}", path);
  }
}
