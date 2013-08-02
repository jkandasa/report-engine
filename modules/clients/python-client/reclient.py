""" ReportEngineClient
"""
import traceback,logging,os,re
import requests,json,StringIO
import jprops
import datetime,pytz

HEADERS = {'Content-Type':'application/json','accept':'application/json'}
TEST_STATUSES = ['Passed', 'Failed', 'Skipped', 'Running', 'NoStatus']
LOG_LEVELS=['ALL','DEBUG', 'INFO', 'WARNING', 'ERROR','CRITICAL', 'DEFAULT']

def _now():
    now = datetime.datetime.now(pytz.utc)
    strnow = now.strftime('%Y-%m-%dT%H:%M:%S')
    msec = '%03d' % now.microsecond
    strnow = "%s.%s" % (strnow, msec[:3])
    return strnow

class ReportEngineClient():
    """
        ReportEngineClient enables you to push test results to report-engine server. Client is configured
        via property file (to keep compatibility with java report-engine client)

        There is an expected workflow with this client:

        1. Create an instance of ReportEngineClient
        2. Configure logging and add ReportEngineCilent.getLogHandler()
        3. Insert a test suite 
        3a. Insert a test group
        4. Insert test (if no test group was inserted, 'default' is inserted instead)
        5. Set test as finished
        6. update test suite 
        
        ReportEngineClient remebers test suite, test group and test that was inserted as last, so when 
        ReportEngineLogHandler starts submitting log records, client knows which test belongs it to.

    """
    def __init__(self,config_file=None,config_file_orig=None):
        """ Creates new instance of report-engine client
            You can specify 2 config files. 'config_file' is a main config. It can reference 'config_file_orig' 
            within a 'ORIGINAL.FILE' config proprety. Original (base) file keeps default values that get overridden
            by values in 'config_file'. If you pass non-null 'config_file_orig' it will be used as original config and
            'ORIGINAL.FILE' option within 'config_file' will be ignored.
        
            :Parameters:
            config_file : string
                File or URL to main config file (java properties format)
            config_file_orig : string
                File or URL to base (original) config file (java properties format)
        """

        self.log = logging.getLogger()
        self.config = None
        self.logHandler = ReportEngineLogHandler(self)
        config = self.read_config_file(config_file)
        if config:
            orig_config = None
            if config_file_orig: # read original (base) file from passed parameter
                orig_config = self.read_config_file(config_file_orig)
            elif config.has_key('ORIGINAL.FILE') and len(config['ORIGINAL.FILE'].strip()) > 0: # or check ORIGINAL.FILE property
                orig_config = self.read_config_file(config['ORIGINAL.FILE'])
            if orig_config: # override values in original config
                orig_config.update(config)
                config = orig_config
            elif config_file_orig:
                raise Exception('Failed to configure ReportEngineClient: invalid original config')
        self.config = config
        if not self.config:
            raise Exception('Failed to configure ReportEngineClient: no config file')
        # check config keys
        for key in ['REPORT.ENGINE.TEST.REFERENCE','REPORT.ENGINE.WATCH.LOGGER','REPORT.ENGINE.TEST.BUILD.VERSION.REFF','REPORT.ENGINE.SERVER.REST.URL','TEST.SUITE.NAME','REPORT.ENGINE.LOGGER.LEVEL']:
            if not self.config.has_key(key):
                raise Exception('Failed to configure ReportEngineClient, missing %s property' % key)
        self.url = config['REPORT.ENGINE.SERVER.REST.URL'].rstrip('/')+'/testresults/'
        # helper dictionary to store test-related information
        self.status = {}
        # retrieve new ID for this testsuite from ReportEngine server
        try:
            self.status['testSuiteId'] = int(self.get('testsuiteid').text)
        except requests.exceptions.RequestException:            
            self.config = None
            raise Exception('Unable to connect to report-engine server %s' % config['REPORT.ENGINE.SERVER.REST.URL'])
       
        if not self.config['REPORT.ENGINE.LOGGER.LEVEL'] in LOG_LEVELS:
            raise Exception('Invalid %s=%s allowed options are %s' %
                    ('REPORT.ENGINE.LOGGER.LEVEL',self.config['REPORT.ENGINE.LOGGER.LEVEL'],str(LOG_LEVELS)))

        self.logHandler.reportLevel = self.config['REPORT.ENGINE.LOGGER.LEVEL']

        if len(self.config['REPORT.ENGINE.TEST.REFERENCE']) == 0:
            raise Exception('Configuration property REPORT.ENGINE.TEST.REFERENCE must not be empty')
        if len(self.config['TEST.SUITE.NAME']) > 0:
                self.status['suiteName'] = self.config['TEST.SUITE.NAME']
        else:
                self.status['suiteName'] = self.config['REPORT.ENGINE.TEST.REFERENCE']
            
    
    def debug(self,record):
        """
            
        """
        pass
        #if type(record) == requests.Response:
        #    record = 'Status: %d Response: %s' % (record.status_code,record.text)
        #with open('/tmp/test.log','a') as fp:
        #    fp.write('Client DEBUG: '+str(record)+'\n')

    def insertSuite(self,name=None):
        """Inserts a new test suite to report-engine server
            
            :Parameters:
            name : string
                Name of testsuite, if None 'TEST.SUITE.NAME' or 'REPORT.ENGINE.TEST.REFERENCE' config option is used
        """

        name = name or self.status['suiteName']
        data = {'id':self.status['testSuiteId'],
                'testStatus':'Running',
                'remoteStartTime':_now(),
                'testSuiteName':name,
                'testReference':self.config['REPORT.ENGINE.TEST.REFERENCE']}
        r = self.post('testsuite',data)
        self.debug(r)

    def updateSuite(self,name=None,status=''):
        """Updates existing testsuite to report-engine server
            
            :Parameters:
            name : string
                Name of testsuite, if None 'TEST.SUITE.NAME' or 'REPORT.ENGINE.TEST.REFERENCE' config option is used
            status : string
                Test suite status, allowed values are 'Running','Completed'
        """
        name = name or self.status['suiteName']
        data = {'id':self.status['testSuiteId'],
                'testStatus':status,
                'testSuiteName':name,
                'testBuild':os.getenv(self.config['REPORT.ENGINE.TEST.BUILD.VERSION.REFF'],''),
                'testReference':self.config['REPORT.ENGINE.TEST.REFERENCE']}
        r = self.put('testsuite',data)
        self.debug(r)


    def insertTestGroup(self,name):
        """ Inserts a new test group to report-engine server
            
            :Parameters:
            name : string
                Name of test-group
        """
        data = {'testSuiteId':self.status['testSuiteId'],'testGroup':name,'remoteTime':_now()}
        r = self.post('testgroup',data)
        self.debug(r)
        self.status['testGroupId'] = r.json()['id']

    def insertTest(self,name):
        """ Inserts a new test case  to report-engine server. This registers
            test case on server
            
            :Parameters:
            name : string
                Name of test case
        """
        if not self.status.has_key('testGroupId'):
            self.insertTestGroup('default')
        
        if self.status.has_key('testCaseId'):
            # test was probably skipped (setTestFinished was not called after insertTest)
            self.setTestFinished(name,'Skipped')

        data = {'testSuiteId':self.status['testSuiteId'],
                'testGroupId':self.status['testGroupId'],
                'testName':name,
                'testResult':'Running',
                'remoteStartTime':_now()
                }
        r = self.post('testcase',data)
        self.debug(r)
        self.status['testCaseId'] = r.json()['id']

    def setTestFinished(self,name,status):
        """ Sets current test (previously added by `insertTest`) as finished
            
            :Parameters:
            name : string
                Name of test case
            status : string
                test case status, available options are 'Passed','Failed','Skipped'
        """
        if not self.status.has_key('testCaseId'):
            self.insertTest(name)
        if not status in TEST_STATUSES:
            raise Exception('Invalid test status, possible values are : '+str(TEST_STATUSES))
        data = {'testSuiteId':self.status['testSuiteId'],
                'testGroupId':self.status['testGroupId'],
                'id':self.status['testCaseId'],
                'testName':name,
                'testResult':status,
                'remoteEndTime':_now()
                }
        r = self.put('testcase',data)
        del self.status['testCaseId']
        self.debug(r)

    def addLogMessage(self,record):
        """ Inserts a new log record to report-engine server
        
            Note: it's not intended to be used by clients, but by :class: `ReportEngineLogHandler`

            :Parameters:
            name : logging.LogRecord
                
        """
        if not (self.status.has_key('testGroupId') and self.status.has_key('testCaseId')):
            # do not proceed when we are not in the middle of test
            return
        if record.name.find('requests.packages') >= 0:
            m = re.search('^(http://|https://)(?P<host>[^:/]+).*$',self.url)
            if m:
                host = m.group('host')
                if record.msg.find(host) >= 0 or record.msg.find('/resteasy/testresults/') > 0:
                    # do not log messages about requests going to report-engine server
                    return
        data = {'testSuiteId':self.status['testSuiteId'],
                'testGroupId':self.status['testGroupId'],
                'testCaseId':self.status['testCaseId'],
                'sequenceNumber':0,
                'logTime':_now(),
                'logLevel':record.levelname,
                'className':record.module,
                'methodName':record.funcName,
                'message':str(record.msg)}
        r = self.post('testlog',data)
        self.debug(r)
    
    def _formatErr(self, err):
        """
            Formats error (a tuple (exctype, value, tb)
        """
        if err:
            exctype, value, tb = err
            return ''.join(traceback.format_exception(exctype, value, tb))
    
    def post(self,resource, data):
        self.debug(data)
        return requests.post(self.url+resource,headers = HEADERS,data=json.dumps(data))
    
    def put(self, resource, data):
        self.debug(data)
        return requests.put(self.url+resource,headers = HEADERS,data=json.dumps(data))

    def get(self, resource):
        return requests.get(self.url+resource,headers = HEADERS,timeout=5)

    def read_config_file(self,config):
        """Reads config file or URL and parses it's content
            
            :Parameters:
            config: string
                File path or URL to config file
        """
        if not config:
            return
        fp = None
        if config.find('http') == 0:
            data = str(requests.get(config).text)
            fp = StringIO.StringIO(data)
        else:
            fp = open(config,'rb')
        if fp:
            props = jprops.load_properties(fp)
            fp.close()
            map(lambda x: x.strip(),props)
            return props

    def getLogHandler(self):
        """
            returns :class: `ReportEngineLogHandler` or :class: `logging.NullHandler`
            based on configuration (property 'REPORT.ENGINE.WATCH.LOGGER'=true|false)
        """
        if self.config and self.config['REPORT.ENGINE.WATCH.LOGGER'].lower() == 'true':
            return self.logHandler
        return logging.NullHandler()

class ReportEngineLogHandler(logging.Handler):
    """
        Log handler that submits logRecords to report engine.
    """

    def __init__(self,client):
        """ Creates new handler instance
        
            Note: it's not intended to be used by clients, but by :class: `ReportEngineClient`

            :Parameters:
            client : :class: `ReportEngineClient`
                
        """
        logging.Handler.__init__(self)
        self.client = client
        self.reportLevel = 'ALL'

    def emit(self,record):
        if self.reportLevel == 'ALL' or self.reportLevel == 'DEFAULT' or LOG_LEVELS.index(self.reportLevel) <= int(record.levelno/10):
            self.client.addLogMessage(record)
