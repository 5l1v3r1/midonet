#!/bin/bash

SANDBOX_NAME="mdts"
SANDBOX_FLAVOUR="default_v1.9_neutron+kilo"
OVERRIDE="sandbox/override_v1"
PROVISIONING="sandbox/provisioning/all-provisioning.sh"

while getopts ":f:o:p:h" opt; do
    case $opt in
    f)
        SANDBOX_FLAVOUR=$OPTARG
        ;;
    o)
        OVERRIDE=$OPTARG
        ;;
    p)
        PROVISIONING=$OPTARG
        ;;
    h)
        echo "$0 [-f SANDBOX_FLAVOUR] [-o OVERRIDE_DIRECTORY]" \
             " [-p PROVISIONING_SCRIPT]"
        exit 1
        ;;
    esac
done

# WARNING: this script is meant to be used on the CI, as it pulls images from
# the CI infrastructure (artifactory)

sudo modprobe openvswitch
sudo modprobe 8021q

# python SSH library (paramiko) dependencies on SSL
# TODO: remove once Jenkins slave image has been regenerated including it
sudo apt-get install --no-install-recommends -y libssl-dev libffi-dev

# install python dependencies (may have changed since image build)
virtualenv venv
. venv/bin/activate
pip install -r tests/mdts.dependencies

# We assume all gates/nightlies put the necessary packages in $WORKSPACE
# so we know where to find them.
mkdir -p tests/$OVERRIDE/packages
cp midolman*.deb tests/$OVERRIDE/packages
cp midonet-api*.deb tests/$OVERRIDE/packages
cp python-midonetclient*.deb tests/$OVERRIDE/packages

# Necessary software in the host, midonet-cli installed from sources
cd python-midonetclient
python setup.py install
cd -

# Install sandbox, directly from repo (ignoring submodule)
sudo rm -rf midonet-sandbox
git clone --depth=1 https://github.com/midonet/midonet-sandbox.git
cd midonet-sandbox
python setup.py install
cd -

# Start sandbox
cd tests/
echo "docker_registry=artifactory.bcn.midokura.com" >> sandbox.conf
echo "docker_insecure_registry=True" >> sandbox.conf
sandbox-manage -c sandbox.conf pull-all $SANDBOX_FLAVOUR
sandbox-manage -c sandbox.conf \
                    run $SANDBOX_FLAVOUR \
                    --name=$SANDBOX_NAME \
                    --override=$OVERRIDE \
                    --provision=$PROVISIONING
cd -
