""" ReportEngineClient
"""
import traceback,logging,os
from nose.plugins import Plugin
from reclient import ReportEngineClient

log = logging.getLogger('nose.plugins.reclient')
class ReportEngine(Plugin):
    """
    """
    name = 'report-engine'

    def help(self):
        return "Enable pushing results to report-engine server"

    def options(self, parser, env=os.environ):
        super(ReportEngine, self).options(parser, env=env)
        parser.add_option('--report-engine-config',
                dest='report_engine_config',
                metavar='FILE|URL',
                default=env.get('NOSE_REPORT_ENGINE_CONFIG'),
                help='Location of ReportEngineClient config file [NOSE_REPORT_ENGINE_CONFIG]')

        parser.add_option('--report-engine-config-orig',
                dest='report_engine_config_orig',
                metavar='FILE|URL',
                default=env.get('NOSE_REPORT_ENGINE_CONFIG_ORIG'),
                help='Location of ReportEngineClient original (base) config file [NOSE_REPORT_ENGINE_CONFIG_ORIG]')

    
    def configure(self, options, conf):
        super(ReportEngine, self).configure(options, conf)
        if not self.enabled:
            return
        config_file = options.report_engine_config
        if not config_file:
            raise Exception('Failed to configure report-engine plugin, use --report-engine-config to provide configuration file')
        config_file_orig = options.report_engine_config_orig
        self.client = ReportEngineClient(config_file,config_file_orig)
        if self.client.config:
            logging.getLogger().addHandler(self.client.getLogHandler())
            log.addHandler(self.client.getLogHandler())
        else:
            self.client = None
        self.last_context = None

    def _testDescription(self,test):
        return test.shortDescription() or str(test)

    def begin(self):
        if self.client:
            self.client.insertSuite()

    def startTest(self, test):
        if self.client:
            if test.context and not self.last_context == test.context:
                self.last_context = test.context
                self.client.insertTestGroup(str(self.last_context.__module__)+'.'+str(self.last_context.__name__))
            self.client.insertTest(self._testDescription(test))

    def addSuccess(self, test):
        if self.client:
            self.client.setTestFinished(self._testDescription(test),'Passed')

    def addSkip(self, test):
        if self.client:
            self.client.setTestFinished(self._testDescription(test),'Skipped')

    def addFailure(self, test, exc):
        if self.client:
            self.debug('@Failed '+self._formatErr(exc))
            log.error(self._formatErr(exc))    
            self.client.setTestFinished(self._testDescription(test),'Failed')
    
    def addError(self, test, exc):
        if self.client:
            self.debug('@Error '+self._formatErr(exc))
            log.error(self._formatErr(exc))    
            self.client.setTestFinished(self._testDescription(test),'Failed')

    def _formatErr(self, err):
        exctype, value, tb = err
        return ''.join(traceback.format_exception(exctype, value, tb))

    def finalize(self, result):
        if self.client:
            self.client.updateSuite(status='Completed')


    def debug(self,record):
        pass
    #    with open('/tmp/test.log','a') as fp:
    #        fp.write('noseplugin DEBUG: '+str(record)+'\n')

