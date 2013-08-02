import sys
try:
    import ez_setup
    ez_setup.use_setuptools()
except ImportError:
    pass

from setuptools import setup

setup(
    name='nose-report-engine',
    version='0.1',
    author='Libor Zoubek',
    author_email = 'lzoubek@redhat.com',
    description = 'This plugin allows you to push test data to ReportEngine server',
    license = 'GNU LGPL',
    install_requires = ['requests>=1.2.0','jprops>=1.0','pytz'],
    url = 'https://github.com/jkandasa/report-engine/tree/master/modules/clients/python-client',
    py_modules = ['noseplugin','reclient'],
    entry_points = {
        'nose.plugins.0.10': [
            'report-engine = noseplugin:ReportEngine'
            ]
        }

    )
