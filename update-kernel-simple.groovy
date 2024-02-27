def product_name
def family_name
def build_agent

pipeline {
    agent any
    
    environment {
        PATH = "${env.PATH}:~/bin/depot_tools/"    
    }
    
    parameters {
        string defaultValue: '192.168.1.102', description: 'DUT IP address', name: 'dut_ip'
        booleanParam defaultValue: true, description: 'Also update firmwares (/lib/firmware)', name: 'update_firmware'

    }

    stages {
        stage('Check DUT') {
            steps {
                sh 'printenv'
                echo "Check DUT IP: ${params.dut_ip}"
                sh "ssh root@${params.dut_ip} 'crossystem'"
                script {
                    product_name = sh(script: "ssh root@${params.dut_ip} 'dmidecode -s baseboard-product-name'", returnStdout: true).trim().toLowerCase()
                    echo "product_name: ${product_name}"
                    
                    def (_m, _f) = sh(script: "ssh root@${params.dut_ip} 'dmidecode -s system-family'", returnStdout: true).trim().toLowerCase().split('_')
                    family_name = _f
                    echo "family_name: ${family_name}"
                    
                    if (family_name == 'brya' || family_name == 'brask') {
                        build_agent = 'cros-brya-d12'
                    } else {
                        build_agent = "cros-${family_name}-d12"
                    }
                    
                    echo "build_agent=${build_agent}"
                }
            }
        }
        
        stage('Remove rootfs verification') {
            steps {
                build job: 'remove-rootfs-verification', parameters: [string(name: 'dut_ip', value: "${params.dut_ip}")]
            }
        }
        
        stage('Update kernel image') {
            agent {
                label "${build_agent}"
            }
            
            steps {
                script {
                    dir("${env.HOME}/cros-tot") {
                        if (update_firmware) {
                            sh "~/bin/depot_tools/cros_sdk /mnt/host/source/src/scripts/update_kernel.sh --board=${family_name} --remote=${params.dut_ip} --firmware"
                        } else {
                            sh "~/bin/depot_tools/cros_sdk /mnt/host/source/src/scripts/update_kernel.sh --board=${family_name} --remote=${params.dut_ip}"
                        }
                    }
                }
            }
        }
    }
}
