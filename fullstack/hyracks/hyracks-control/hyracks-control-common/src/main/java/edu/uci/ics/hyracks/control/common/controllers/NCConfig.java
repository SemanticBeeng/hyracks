/*
 * Copyright 2009-2010 by The Regents of the University of California
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * you may obtain a copy of the License from
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.uci.ics.hyracks.control.common.controllers;

import java.io.Serializable;
import java.util.List;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.StopOptionHandler;

public class NCConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    @Option(name = "-cc-host", usage = "Cluster Controller host name", required = true)
    public String ccHost;

    @Option(name = "-cc-port", usage = "Cluster Controller port (default: 1099)")
    public int ccPort = 1099;

    @Option(name = "-cluster-net-ip-address", usage = "IP Address to bind cluster listener", required = true)
    public String clusterNetIPAddress;

    @Option(name = "-node-id", usage = "Logical name of node controller unique within the cluster", required = true)
    public String nodeId;

    @Option(name = "-data-ip-address", usage = "IP Address to bind data listener", required = true)
    public String dataIPAddress;

    @Option(name = "-result-ip-address", usage = "IP Address to bind dataset result distribution listener", required = true)
    public String datasetIPAddress;

    @Option(name = "-iodevices", usage = "Comma separated list of IO Device mount points (default: One device in default temp folder)", required = false)
    public String ioDevices = System.getProperty("java.io.tmpdir");

    @Option(name = "-net-thread-count", usage = "Number of threads to use for Network I/O (default: 1)")
    public int nNetThreads = 1;

    @Option(name = "-max-memory", usage = "Maximum memory usable at this Node Controller in bytes (default: -1 auto)")
    public int maxMemory = -1;

    @Option(name = "-result-history-size", usage = "Limits the number of jobs whose results should be remembered by the system to the specified value. (default: 10)")
    public int resultHistorySize = 100;

    @Option(name = "-result-manager-memory", usage = "Memory usable for result caching at this Node Controller in bytes (default: -1 auto)")
    public int resultManagerMemory = -1;

    @Option(name = "-app-nc-main-class", usage = "Application NC Main Class")
    public String appNCMainClass;

    @Argument
    @Option(name = "--", handler = StopOptionHandler.class)
    public List<String> appArgs;

    public void toCommandLine(List<String> cList) {
        cList.add("-cc-host");
        cList.add(ccHost);
        cList.add("-cc-port");
        cList.add(String.valueOf(ccPort));
        cList.add("-cluster-net-ip-address");
        cList.add(clusterNetIPAddress);
        cList.add("-node-id");
        cList.add(nodeId);
        cList.add("-data-ip-address");
        cList.add(dataIPAddress);
        cList.add(datasetIPAddress);
        cList.add("-iodevices");
        cList.add(ioDevices);
        cList.add("-net-thread-count");
        cList.add(String.valueOf(nNetThreads));
        cList.add("-max-memory");
        cList.add(String.valueOf(maxMemory));
        cList.add("-result-history-size");
        cList.add(String.valueOf(resultHistorySize));
        cList.add("-result-manager-memory");
        cList.add(String.valueOf(resultManagerMemory));

        if (appNCMainClass != null) {
            cList.add("-app-nc-main-class");
            cList.add(appNCMainClass);
        }
        if (appArgs != null && !appArgs.isEmpty()) {
            cList.add("--");
            for (String appArg : appArgs) {
                cList.add(appArg);
            }
        }
    }
}
