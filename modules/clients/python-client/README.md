# nose-report-engine 

Is a client for report-engine for tests written in python. It more or less shares a way of configuration with 
report-engine java-client This project contains 2 modules and sample config file.
 * reclient.py - a generic client for report-engine
 * noseplugin.py - [nosetests](http://nose.readthedocs.org) plugin adding support for report-engine

## Installation

Install using setuptools ```sudo python setup.py install```

## Usage

Edit your ```ReportEngineClient.properties```

```
nosetests --with-report-engine  --report-engine-config=ReportEngineClient.properties
```

Or if you have company-wide shared configuration you can use

```
nosetests --with-report-engine  --report-engine-config=ReportEngineClient.properties 
--report-engine-config-orig=http://example.com/ReportEngineClientBaseConfig.properties
