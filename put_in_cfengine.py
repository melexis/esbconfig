#!/usr/bin/env python

""" Copy the masterslave config files to their respective branch. """

import getopt
import os
import re
import subprocess
import shlex, shutil
import sys

def filenames_for_env(filenames, env):
    """ Filter out the filenames for the given environment

    Given a list of filenames:
    >>> filenames = [ 'esb-a-test.colo.elex.be-masterslave.xml'
    ...             , 'esb-a-test.colo.elex.be-masterslave.xml'
    ...             , 'esb-a-uat.colo.elex.be-masterslave.xml' ]

    When i call filenames_for_env i get a list of filenames that match that environment.
    >>> filenames_for_env(filenames, '-test')
    ['esb-a-test.colo.elex.be-masterslave.xml', 'esb-a-test.colo.elex.be-masterslave.xml']

    It can also filter out the filenames for production:
    >>> prod_filenames = [ 'esb-a.colo.elex.be-masterslave.xml'
    ...                  , 'esb-a-test.colo.elex.be-masterslave.xml'
    ...                  , 'esb-b.sensors.elex.be-masterslave.xml']

    >>> filenames_for_env(prod_filenames, '')
    ['esb-a.colo.elex.be-masterslave.xml', 'esb-b.sensors.elex.be-masterslave.xml']

    It also filters out filenames in directories for production:
    >>> filenames_with_dir = [ 'target/esb-a.colo.elex.be-masterslave.xml' ]
    >>> filenames_for_env(filenames_with_dir, '')
    ['target/esb-a.colo.elex.be-masterslave.xml']
    """
    return [filename for filename in filenames if re.search('esb-\w%s\.' % env, filename)]

def copy_configs(src, dst, env):
    """ Copy the configs that apply for that environment to the destination """
    filenames = os.listdir(src)
    applicablefiles = [src + '/' + filename for filename in filenames_for_env(filenames, env)]
    for af in applicablefiles:
        shutil.copy(af, dst)

def git(command, cwd='.'):
    cmd = ['git'] + shlex.split(command)
    p = subprocess.call(cmd, cwd=cwd)


def checkout_cfengine_branch(cfenginepath, branch, base):
    cmd = 'checkout -b %s %s' % (branch, base)
    git(cmd, cfenginepath)

def put_config_in_new_branch(cfenginepath, branch, base, filter, destination):
    checkout_cfengine_branch(cfenginepath, branch, base)
    files = os.listdir('target')
    files_to_copy = filenames_for_env(files, filter)
    for f in files_to_copy:
        shutil.copyfile(f, cfenginepath + '/' + destination)
    git('commit -a -m "Updated config files for activemq"')

def test():
    import doctest
    doctest.testmod()

    copy_configs('target', '/tmp', 'test')

def main():
    testbranch = None
    uatbranch = None
    prodbranch = None
    cfenginepath = '../cfengine'

    options, remainder = getopt.getopt(sys.argv[1:], 't:u:p:c:x', 
            ['testbranch=', 'uatbranch=', 'prodbranch=', 'cfenginepath=', 'test'])

    for opt, arg in options:
        if opt in ('-t', '--testbranch'):
            testbranch = arg
        elif opt in ('-u', '--uatbranch'):
            uatbranch = arg
        elif opt in ('-p', '--prodbranch'):
            prodbranch = arg
        elif opt in ('-c', '--cfenginepath'):
            cfenginepath = arg
        elif opt in ('-x', '--test'):
            test()
            exit()

    git('fetch origin', cfenginepath)

    if testbranch is not None:
        put_config_in_new_branch(cfenginepath, testbranch, 'origin/TEST', 'test', 'masterfiles/files/esb')
    if uatbranch is not None:
        put_config_in_new_branch(cfenginepath, testbranch, 'origin/UAT', 'uat', 'masterfiles/files/esb')
    if prodbranch != None:
        put_config_in_new_branch(cfenginepath, testbranch, 'origin/STABLE', 'uat', 'masterfiles/files/esb')

if __name__ == '__main__':
    main()
