def product_name
def is_factory_enabled = false

pipeline {
    agent {
        label 'jenkins-master'
    }
    
    parameters {
        string defaultValue: '192.168.1.102', description: 'DUT IP address', name: 'dut_ip'
    }

    stages {
        stage('Check DUT') {
            steps {
                sh 'printenv'
                echo "Check DUT IP: ${params.dut_ip}"
                sh "ssh root@${params.dut_ip} 'crossystem'"
                script {
                    product_name = sh(script: "ssh root@${params.dut_ip} 'dmidecode -s baseboard-product-name'", returnStdout: true).trim()
                    echo "product_name: ${product_name}"
                }
            }
        }
        
        stage('install factory toolkit') {
            steps {
                script {
                    is_factory_enabled = sh(script: "ssh root@${params.dut_ip} 'test -f /usr/local/factory/enabled'", returnStatus: true) ? false : true
                    
                    if (is_factory_enabled == true) {
                        echo "DUT already in factory mode"
                    } else {
                        echo "DUT not in factory mode, install factory toolkit..."
                        sh """
                            #!/bin/bash
                            ssh root@${params.dut_ip} << EOF
                            cd /usr/local/factory-toolkit
                            echo "y" | ./install_factory_toolkit.run
                            << EOF
                        """
                    }
                }
            }
        }
        
        stage('Reboot DUT') {
            when {
                expression { return is_factory_enabled == false }
            }
            steps {
                catchError(buildResult: 'SUCCESS', stageResult: 'SUCCESS') {
                    timeout(time: 15, unit: "SECONDS") {
                        sh "ssh root@${params.dut_ip} 'reboot'"
                    }
                }
                
                // Wait the DUT rebooting
                sleep(time:10, unit: "SECONDS")
                retry(20) {
                    timeout(time: 10, unit: "SECONDS") {
                        sh "ping -c 3 ${params.dut_ip}"
                    }
                }
            }
        }
    }
}
