package com.hortonworks.yarnapp;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.yarn.api.ApplicationConstants.Environment;
import org.apache.hadoop.yarn.api.protocolrecords.GetNewApplicationResponse;
import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.hadoop.yarn.api.records.ApplicationSubmissionContext;
import org.apache.hadoop.yarn.api.records.ContainerLaunchContext;
import org.apache.hadoop.yarn.api.records.LocalResource;
import org.apache.hadoop.yarn.api.records.LocalResourceType;
import org.apache.hadoop.yarn.api.records.LocalResourceVisibility;
import org.apache.hadoop.yarn.api.records.NodeReport;
import org.apache.hadoop.yarn.api.records.NodeState;
import org.apache.hadoop.yarn.api.records.QueueInfo;
import org.apache.hadoop.yarn.api.records.Resource;
import org.apache.hadoop.yarn.api.records.YarnClusterMetrics;
import org.apache.hadoop.yarn.client.api.YarnClient;
import org.apache.hadoop.yarn.client.api.YarnClientApplication;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.exceptions.YarnException;
import org.apache.hadoop.yarn.util.ConverterUtils;
import org.apache.hadoop.yarn.util.Records;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AppClient {
  private static final Logger LOG = LoggerFactory.getLogger(AppClient.class);
  private static final String APP_NAME = "YarnApp";
  private YarnConfiguration conf;
  private YarnClient yarnClient;
  private String appJar = "yarnapp.jar";
  private ApplicationId appId;
  private FileSystem fs;

  public static void main(String[] args) {
    AppClient client = new AppClient(args);
    try {
      boolean result = client.run();
    }
    catch (YarnException | IOException e) {
      e.printStackTrace();
    }
  }

  public AppClient(String[] args) {
    conf = new YarnConfiguration();
    yarnClient = YarnClient.createYarnClient();
    yarnClient.init(conf);
  }

  public boolean run() throws YarnException, IOException {
    yarnClient.start();
    YarnClientApplication client = yarnClient.createApplication();
    GetNewApplicationResponse appResponse = client.getNewApplicationResponse();
    appId = appResponse.getApplicationId();
    LOG.info("Applicatoin ID = {}", appId);
    int maxMemory = appResponse.getMaximumResourceCapability().getMemory();
    int maxVCores = appResponse.getMaximumResourceCapability()
        .getVirtualCores();
    LOG.info("Max memory = {} and max vcores = {}", maxMemory, maxVCores);
    YarnClusterMetrics clusterMetrics = yarnClient.getYarnClusterMetrics();
    LOG.info("Number of NodeManagers = {}", clusterMetrics.getNumNodeManagers());

    List<NodeReport> nodeReports = yarnClient.getNodeReports(NodeState.RUNNING);
    for (NodeReport node : nodeReports) {
      LOG.info("Node ID = {}, address = {}, containers = {}", node.getNodeId(),
          node.getHttpAddress(), node.getNumContainers());
    }
    List<QueueInfo> queueList = yarnClient.getAllQueues();
    for (QueueInfo queue : queueList) {
      LOG.info("Available queue: {} with capacity {} to {}",
          queue.getQueueName(), queue.getCapacity(), queue.getMaximumCapacity());
    }

    fs = FileSystem.get(conf);
    Path src = new Path(this.appJar);
    String pathSuffix = APP_NAME + "/" + appId.getId() + "/app.jar";
    Path dest = new Path(fs.getHomeDirectory(), pathSuffix);
    fs.copyFromLocalFile(false, true, src, dest);
    FileStatus destStatus = fs.getFileStatus(dest);

    LocalResource jarResource = Records.newRecord(LocalResource.class);
    jarResource.setResource(ConverterUtils.getYarnUrlFromPath(dest));
    jarResource.setSize(destStatus.getLen());
    jarResource.setTimestamp(destStatus.getModificationTime());
    jarResource.setType(LocalResourceType.FILE);
    jarResource.setVisibility(LocalResourceVisibility.APPLICATION);
    Map<String, LocalResource> localResources = new HashMap<>();
    localResources.put("app.jar", jarResource);

    Map<String, String> env = new HashMap<>();
    String appJarDest = dest.toUri().toString();
    env.put("AMJAR", appJarDest);
    LOG.info("AMJAR environment variable is set to {}", appJarDest);
    env.put("AMJARTIMESTAMP", Long.toString(destStatus.getModificationTime()));
    env.put("AMJARLEN", Long.toString(destStatus.getLen()));

    StringBuilder classPathEnv = new StringBuilder().append(
        File.pathSeparatorChar).append("./app.jar");
    for (String c : conf.getStrings(
        YarnConfiguration.YARN_APPLICATION_CLASSPATH,
        YarnConfiguration.DEFAULT_YARN_APPLICATION_CLASSPATH)) {
      classPathEnv.append(File.pathSeparatorChar);
      classPathEnv.append(c.trim());
    }
    classPathEnv.append(File.pathSeparatorChar);
    classPathEnv.append(Environment.CLASSPATH.$());
    env.put("CLASSPATH", classPathEnv.toString());

    ApplicationSubmissionContext appContext = client
        .getApplicationSubmissionContext();
    appContext.setApplicationName(APP_NAME);
    ContainerLaunchContext amContainer = Records
        .newRecord(ContainerLaunchContext.class);
    amContainer.setLocalResources(localResources);
    amContainer.setEnvironment(env);
    
    Vector<CharSequence> vargs = new Vector<>(30);
    vargs.add(Environment.JAVA_HOME.$() + "/bin/java");
    vargs.add("com.hortonworks.yarnapp.ApplicationMaster");
    vargs.add("1><LOG_DIR>/AppMaster.stdout");
    vargs.add("2><LOG_DIR>/AppMaster.stderr");
    StringBuilder command = new StringBuilder();
    for (CharSequence str : vargs) {
         command.append(str).append(" ");
    }
    List<String> commands = new ArrayList<>();
    commands.add(command.toString());
    amContainer.setCommands(commands);
    LOG.info("Command to execute ApplicationMaster = {}", command);
    
    Resource capability = Records.newRecord(Resource.class);
    capability.setMemory(1024);
    appContext.setResource(capability);

    appContext.setAMContainerSpec(amContainer);
    yarnClient.submitApplication(appContext);
    
    return true;
  }

}
