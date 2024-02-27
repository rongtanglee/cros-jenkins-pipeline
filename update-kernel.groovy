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
        
        choice choices: ['v5.10', 'v5.15', 'v6.1', 'v6.6', 'upstream'], description: 'Linux kernel version', name: 'kernel_version'
        string defaultValue: '', description: 'Commit ID (checkout to this commit_id if specified)', name: 'commit_id'
        string defaultValue: 'remotes/m/main', description: 'Remote Branch (checkout to this remote branch if commit_id is not specified)', name: 'remote_branch'

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
        
        stage('Build kernel image') {
            steps {
                build job: 'build-kernel', parameters: [string(name: 'board', value: "${family_name}"), string(name: 'kernel_version', value: "${params.kernel_version}"), string(name: 'commit_id', value: "${params.commit_id}"), string(name: 'remote_branch', value: 'remotes/m/main'), string(name: 'local_branch', value: 'for-kernel-update')]
            }
        }
        
        stage('Remove rootfs verification') {
            steps {
                build job: 'remove-rootfs-verification', parameters: [string(name: 'dut_ip', value: "${params.dut_ip}")]
            }
        }
        
        stage('Update kernel image') {
            steps {
                build job: 'update-kernel-simple', parameters: [string(name: 'dut_ip', value: "${params.dut_ip}"), booleanParam(name: 'update_firmware', value: "${params.update_firmware}")]
            }
        }
    }
}
