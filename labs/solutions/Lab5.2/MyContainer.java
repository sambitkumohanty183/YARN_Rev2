package com.hortonworks.yarnapp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.net.NetUtils;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MyContainer {
	private static final Logger LOG = LoggerFactory.getLogger(MyContainer.class);
	private String hostname;
	private YarnConfiguration conf;
	private FileSystem fs;
	private Path inputFile;
	private long start;
	private int length;
	
	public MyContainer(String[] args) throws IOException {
		hostname = NetUtils.getHostname();
		conf = new YarnConfiguration();
		fs = FileSystem.get(conf);
		inputFile = new Path(args[0]);
		start = Long.parseLong(args[1]);
		length = Integer.parseInt(args[2]);
	}

	public static void main(String[] args) {
		LOG.info("Container just started on {}", NetUtils.getHostname());
		try {
			MyContainer container = new MyContainer(args);
			container.run();
		} catch (IOException e) {
			e.printStackTrace();
		}
		LOG.info("Container is ending...");
	}

	private void run() throws IOException {
		LOG.info("Running Container on {}", this.hostname);
		FSDataInputStream fsdis = fs.open(inputFile);
		fsdis.seek(this.start);
		BufferedReader reader = new BufferedReader(new
		InputStreamReader(fsdis));
		LOG.info("Reading from {} to {} from {}", start, start +
		length, inputFile.toString());
		String current = "";
		long bytesRead = 0;
		while (bytesRead < this.length
				&& (current = reader.readLine()) != null) {
			bytesRead += current.getBytes().length;
			if (current.contains("CLINTON")) {
				LOG.info("Found CLINTON: {}", current);
			}
		}
	}

}
