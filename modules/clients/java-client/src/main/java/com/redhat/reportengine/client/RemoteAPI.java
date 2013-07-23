/**
 * 
 */
package com.redhat.reportengine.client;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.URL;
import java.security.CodeSource;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.ImageIO;

import org.apache.commons.codec.binary.Base64;

import com.redhat.reportengine.server.dbmap.TestCase;
import com.redhat.reportengine.server.dbmap.TestGroup;
import com.redhat.reportengine.server.dbmap.TestLogs;
import com.redhat.reportengine.server.dbmap.TestSuite;
import com.redhat.reportengine.server.restapi.testresult.TestResultsRestUrlMap;

/**
 * @author jkandasa@redhat.com (Jeeva Kandasamy)
 * Mar 16, 2012
 */
public class RemoteAPI {
	protected static final Logger _logger = Logger.getLogger(RemoteAPI.class.getName());
	ClientCommon test = new ClientCommon();
	protected static boolean initialized = false;
	private static boolean clientConfigurationSuccess = false;
	private static boolean clientLoadedSuccess = false;

	private static final String rePropertyFile 		= "REPORT_ENGINE_PROPERTY_FILE";	
	private static final String originalFile 		= "ORIGINAL.FILE";
	private static final String testReference 		= "REPORT.ENGINE.TEST.REFERENCE";
	private static final String screenShot		 	= "REPORT.ENGINE.TAKE.SCREEN.SHOT";
	private static final String buildVersion	 	= "REPORT.ENGINE.TEST.BUILD.VERSION.REFF";
	private static final String watchLogger	 		= "REPORT.ENGINE.WATCH.LOGGER";
	private static final String loggerType	 		= "REPORT.RENGINE.LOGGER.TYPE";
	private static final String logLevel	 		= "REPORT.ENGINE.LOGGER.LEVEL";
	private static final String serverRESTUrl 		= "REPORT.ENGINE.SERVER.REST.URL";

	private static final String testSuiteName 		= "TEST.SUITE.NAME";

	protected static final String loggersToWarn		= 
			"org.apache.http," +
			"com.sun.xml.bind," +
			"com.sun.jersey," +
			"org.jboss.resteasy";

	private RESTClientRestEasy restClient = null;

	public RemoteAPI() {
		super();
		//For JUL
		this.setJulLoggers(loggersToWarn, Level.WARNING);
		//For LOG4J
		this.setLog4jLoggers(loggersToWarn, org.apache.log4j.Level.WARN);
		_logger.log(Level.INFO, loggersToWarn+", set to "+Level.WARNING+" Level...");
	}

	public boolean isLastTestStateRunning(){
		if(test.getTestCaseId() != null){
			return true;
		}
		return false;		
	}

	public String getBuildVersionReference(){
		return test.getBuildVersionReference();
	}
	public Date getServerTime() throws Exception{
		return new SimpleDateFormat(TestResultsRestUrlMap.DATE_FORMAT).parse((String)restClient.get(TestResultsRestUrlMap.GET_SERVER_TIME, String.class));
	}
	/**
	 * reads properties from given location
	 * @param props original properties, new properties loaded by given location will override values
	 * @param location either local file or url (starting with http) to read properties from
	 * @return
	 * @throws Exception when location is null or location could not be read
	 */
	private Properties loadProperties(Properties props, String location) throws Exception {
	    if (location == null) {
		throw new Exception("Cannot read property file : it's null");
	    }
	    if (props == null) {
		props = new Properties();
	    }
	    if (location.startsWith("http")) {
		URL url = new URL(location);
		BufferedReader in = null;
		try {
		    in = new BufferedReader(new InputStreamReader(url.openStream()));
		    props.load(in);
		}
		finally {
		    if (in!=null) {
			in.close();
		    }
		}
	    }
	    else {
		props.load(new FileReader(location));
	    }
	    return props;
	}

	private void setAllVariables(){
		try{
			Properties propertyFile = new Properties();
			String primaryPropertyFile = null;

			_logger.log(Level.INFO, "System.getenv: "+System.getenv(rePropertyFile));
			_logger.log(Level.INFO, "System.getProperty: "+System.getProperty(rePropertyFile));

			if(System.getenv(rePropertyFile) != null){				//get property file from system ENV variable
				primaryPropertyFile = System.getenv(rePropertyFile).trim(); 
			}else if(System.getProperty(rePropertyFile) != null){ 	//Get Property file by System Property variable
				primaryPropertyFile = System.getProperty(rePropertyFile).trim(); 
			}else{ 													//Get Property file by jar location
				CodeSource codeSource = RemoteAPI.class.getProtectionDomain().getCodeSource();
				File jarFile = new File(codeSource.getLocation().toURI().getPath());
				File jarDir = jarFile.getParentFile();

				if (jarDir != null && jarDir.isDirectory()) {
					primaryPropertyFile = jarDir+"/ReportEngineClient.properties"; 

				}
			}

			_logger.log(Level.INFO, "Properties File: "+primaryPropertyFile);
			if(primaryPropertyFile != null){
				propertyFile = loadProperties(propertyFile,primaryPropertyFile);
			}else{
				this.setClientConfigurationSuccess(false);
				_logger.log(Level.WARNING, "Could not find report engine properties File any where, Please update property file by atleast one of the way, Disabled Report Engine logs!!");			
			}

			String origPropertyFileName = propertyFile.getProperty(originalFile); // get orig property file name form primary property file
			if(System.getenv(originalFile) != null){ //get property file from system ENV variable
			    origPropertyFileName = System.getenv(originalFile).trim(); 
			}else if(System.getProperty(originalFile) != null){ //Get Property file by System Property variable
			    origPropertyFileName = System.getProperty(originalFile).trim(); 
			}

			if((origPropertyFileName!= null) && origPropertyFileName.length() > 0){
				_logger.log(Level.INFO, "Properties File(New): "+origPropertyFileName);
				Properties original = loadProperties(new Properties(), origPropertyFileName);
				// reload main properties with original values ad defaults
				propertyFile = loadProperties(original, primaryPropertyFile);
			}

			test.setServerRestUrl(propertyFile.getProperty(serverRESTUrl).trim());
			test.setTestReference(propertyFile.getProperty(testReference).trim());
			test.setTakeScreenShot(Boolean.parseBoolean(propertyFile.getProperty(screenShot).trim()));
			test.setLoggerEnabled(Boolean.parseBoolean(propertyFile.getProperty(watchLogger).trim()));
			test.setLoggerType(propertyFile.getProperty(loggerType).trim());
			test.setLogLevel(propertyFile.getProperty(logLevel).trim());
			test.setBuildVersionReference(propertyFile.getProperty(buildVersion).trim());

			if((propertyFile.getProperty(testSuiteName) != null) && propertyFile.getProperty(testSuiteName).trim().length() > 0){
				test.setTestSuiteName(propertyFile.getProperty(testSuiteName));
			}

			this.setClientConfigurationSuccess(true);
		}catch(Exception ex){
			this.setClientConfigurationSuccess(false);
			ex.printStackTrace();
		}

	}

	public void initClient(String testSuiteName) throws Exception{
		restClient = new RESTClientRestEasy();
		initClient(testSuiteName, null);
	}
	public void initClient(String testSuiteName, String comments) throws Exception{
		if(!initialized){
			doInitialize(testSuiteName, comments);
			if(this.isClientConfigurationSuccess()){
				runLogHandler();
			}
			initialized = true;
		}
	}

	protected void setJulLoggers(String loggers, Level level){
		for(String logger : loggers.split(",")){
			Logger.getLogger(logger.trim()).setLevel(level);
		}
	}
	
	protected void setLog4jLoggers(String loggers, org.apache.log4j.Level level){
		for(String logger : loggers.split(",")){
			org.apache.log4j.Logger.getLogger(logger.trim()).setLevel(level);
		}
	}

	public void runLogHandler(){
		if(test.isLoggerEnabled()){
			LogHandler.setRemoteApi(this);
			LogHandler.initLogger(); 		// Initialize Log watcher
			_logger.log(Level.INFO, "Enabled Watch Logger, Logger Type: "+test.getLoggerType());
		}
	}


	private void doInitialize(String testSuiteName, String comments) throws Exception{
		setAllVariables();		
		if(this.isClientConfigurationSuccess()){
			restClient.setServerUrl(test.getServerRestUrl());
			restClient.setRootUrl(TestResultsRestUrlMap.ROOT);
			_logger.log(Level.INFO, "Report Engine Server REST URL: "+test.getServerRestUrl());
			_logger.log(Level.INFO, "Report Engine Server Time: "+getServerTime());
			this.insertTestSuite(testSuiteName, comments);
			_logger.log(Level.INFO, "Report Engine Client Loaded successfully...");		
			RemoteAPI.setClientLoadedSuccess(true);
		}else{
			_logger.log(Level.WARNING, "Report Engine Client Failed to Load..");
		}		
	}

	public void insertTestSuite(String TestSuitName) throws Exception{
		insertTestSuite(TestSuitName, null);
	}

	public void insertTestSuite(String TestSuitName, String comments) throws Exception{
		test.setTestSuiteId(Integer.valueOf((String)restClient.get(TestResultsRestUrlMap.GET_TESTSUITE_NEXT_AVAILABLE_ID, String.class)));
		TestSuite testSuite = new TestSuite();
		testSuite.setId(test.getTestSuiteId());
		testSuite.setRemoteStartTime(new Date());
		testSuite.setTestComments(comments);
		testSuite.setTestStatus(TestSuite.RUNNING);
		if(test.getTestSuiteName() != null){
			testSuite.setTestSuiteName(test.getTestSuiteName());
		}else{
			testSuite.setTestSuiteName(TestSuitName);
		}		
		testSuite.setTestReference(test.getTestReference());
		testSuite.setTestHost(InetAddress.getLocalHost().getHostName()+" ["+InetAddress.getLocalHost().getHostAddress()+"]");	
		restClient.post(TestResultsRestUrlMap.INSERT_TESTSUITE, testSuite, TestSuite.class);
	}

	public void updateTestSuiteName(String testSuiteName) throws Exception{
		updateTestSuiteName(testSuiteName, null);
	}

	public void updateTestSuiteName(String testSuiteName, String comments) throws Exception{
		TestSuite testSuite = new TestSuite();
		testSuite.setId(test.getTestSuiteId());
		if(test.getTestSuiteName() != null){
			testSuite.setTestSuiteName(test.getTestSuiteName());
		}else{
			testSuite.setTestSuiteName(testSuiteName);
		}	
		testSuite.setTestComments(comments);
		restClient.put(TestResultsRestUrlMap.UPDATE_TESTSUITE_NAME, testSuite, TestSuite.class);
	}


	public void updateTestSuite(String status, String build) throws Exception{
		updateTestSuite(status, build, null);
	}
	public void updateTestSuite(String status, String build, String comments) throws Exception{
		TestSuite testSuite = new TestSuite();
		testSuite.setId(test.getTestSuiteId());
		testSuite.setTestStatus(status);
		testSuite.setTestBuild(build);
		testSuite.setTestComments(comments);
		testSuite.setRemoteEndTime(new Date());
		restClient.put(TestResultsRestUrlMap.UPDATE_TESTSUITE, testSuite, TestSuite.class);
	}

	public void insertTestGroup(String groupName) throws Exception{
		if(!test.getTestGroupName().equals(groupName)){
			TestGroup testGroup = new TestGroup();		
			testGroup.setTestSuiteId(test.getTestSuiteId());
			testGroup.setTestGroup(groupName);	
			testGroup.setRemoteTime(new Date());
			testGroup = (TestGroup) restClient.post(TestResultsRestUrlMap.INSERT_TESTGROUP, testGroup, TestGroup.class);
			test.setTestGroupId(testGroup.getId());
			test.setTestGroupName(groupName);
		}		
	}

	public void insertTestCase(String testCaseName, String result) throws Exception{
		insertTestCase(testCaseName, null, result);
	}
	public void insertTestCase(String testCaseName, String arguments, String result) throws Exception{
		TestCase testCase = new TestCase();
		testCase.setTestSuiteId(test.getTestSuiteId());
		testCase.setTestGroupId(test.getTestGroupId());
		testCase.setTestName(testCaseName);
		testCase.setTestArguments(arguments);
		testCase.setTestResult(result);
		testCase.setRemoteStartTime(new Date());
		test.setTestCaseId(((TestCase) restClient.post(TestResultsRestUrlMap.INSERT_TESTCASE, testCase, TestCase.class)).getId());
		test.setScreenShotFileName(null);
		if(!result.equalsIgnoreCase(TestCase.RUNNING)){
			test.setTestCaseId(null);
		}
	}

	public void updateTestCase(String status) throws Exception{
		updateTestCase(status, null);
	}

	public void updateTestCase(String status, String comments) throws Exception{
		if(test.getTestCaseId() == null){
			_logger.log(Level.INFO, "Seems selected test already get finished...skipping...");
			return;
		}
		TestCase testCase = new TestCase();
		testCase.setId(test.getTestCaseId());
		testCase.setTestResult(status);
		if(comments != null){
			testCase.setTestComments(comments);
		}	

		if(test.getScreenShotFileName() != null){
			testCase.setScreenShotFileName(test.getScreenShotFileName());
			testCase.setScreenShotFileBase64(test.getScreenShotFileBase64String());
		}
		testCase.setRemoteEndTime(new Date());
		restClient.put(TestResultsRestUrlMap.UPDATE_TESTCASE, testCase, TestCase.class);
		if(!status.equalsIgnoreCase(TestCase.RUNNING)){
			test.setTestCaseId(null);
		}
	}

	//To take Screen shot
	public void takeScreenShot(){
		if(!test.isTakeScreenShot()){
			return;
		}
		try{
			_logger.log(Level.FINE, "Taking screen shot...");
			String fileName = "ScreenShot_"+new SimpleDateFormat("dd_MMM_yyyy_hh_mm_ssaa").format(new Date())+".png";
			Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
			Rectangle screenRectangle = new Rectangle(screenSize);
			Robot robot = new Robot();
			BufferedImage image = robot.createScreenCapture(screenRectangle);			
			//image to bytes
			ByteArrayOutputStream byteArrayStream = new ByteArrayOutputStream();
			ImageIO.write(image, "png", byteArrayStream);
			byteArrayStream.flush();
			byte[] imageAsRawBytes = byteArrayStream.toByteArray();
			byteArrayStream.close();	        
			//bytes to string
			test.setScreenShotFileBase64String(new String(Base64.encodeBase64(imageAsRawBytes)));	
			_logger.log(Level.FINE, "ScreenShot File name: {0}, Screen shot done", fileName);
			test.setScreenShotFileName(fileName);
		}catch(Exception ex){
			_logger.log(Level.WARNING, "Unable to take screen shot,", ex);
		}

	}

	public void insertLogMessage(TestLogs testLogs) throws Exception{
		testLogs.setTestSuiteId(test.getTestSuiteId());
		if(test.getTestGroupId() != null){
			testLogs.setTestGroupId(test.getTestGroupId());
		}
		if(test.getTestCaseId() != null){
			testLogs.setTestCaseId(test.getTestCaseId());	
		}		
		restClient.post(TestResultsRestUrlMap.INSERT_TESTLOG, testLogs, TestLogs.class);
	}

	public String getLoggerType(){
		return test.getLoggerType();
	}

	public String getLogLevel(){
		return test.getLogLevel();
	}

	/**
	 * @return the clientConfigurationSuccess
	 */
	public boolean isClientConfigurationSuccess() {
		return clientConfigurationSuccess;
	}

	/**
	 * @param clientConfigurationSuccess the clientConfigurationSuccess to set
	 */
	public void setClientConfigurationSuccess(boolean clientConfigurationSuccess) {
		RemoteAPI.clientConfigurationSuccess = clientConfigurationSuccess;
	}

	/**
	 * @return the clientLoadedSuccess
	 */
	public static boolean isClientLoadedSuccess() {
		return clientLoadedSuccess;
	}

	/**
	 * @param clientLoadedSuccess the clientLoadedSuccess to set
	 */
	public static void setClientLoadedSuccess(boolean clientLoadedSuccess) {
		RemoteAPI.clientLoadedSuccess = clientLoadedSuccess;
	}
}
