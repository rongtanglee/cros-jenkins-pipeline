def product_name
def current_dt

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
                sh "ssh root@${params.dut_ip} -t 'crossystem'"
                script {
                    product_name = sh(script: "ssh root@${params.dut_ip} 'dmidecode -s baseboard-product-name'", returnStdout: true).trim()
                    echo "product_name: ${product_name}"
                    
                    Date date = new Date()
                    current_dt = date.format('yyyyMMdd-HHmmss')
                    echo "current_dt: ${current_dt}"
                }
            }
        }
        
        stage('Modify linux cmdline') {
            steps {
                script {
                    def has_bootchart_arg = sh(script: "ssh root@${params.dut_ip} 'grep -q cros_bootchart /proc/cmdline'", returnStatus: true)
                    
                    if (has_bootchart_arg == 0) {
                        echo "The /proc/cmdline contains 'cros_bootchart'"
                        sh """
                            ssh root@${params.dut_ip} << EOF
                            /usr/share/vboot/bin/make_dev_ssd.sh --save_config /usr/local/cmdline-orig --partitions 2
                            sed -i 's/cros_bootchart//g' /usr/local/cmdline-orig.2
                        """
                    } else {
                        sh """
                            ssh root@${params.dut_ip} << EOF
                            /usr/share/vboot/bin/make_dev_ssd.sh --save_config /usr/local/cmdline-orig --partitions 2
                            cp /usr/local/cmdline-orig.2 /usr/local/cmdline-bootchart.2
                            sed -i ' 1 s/.*/& cros_bootchart/' /usr/local/cmdline-bootchart.2
                            cat /usr/local/cmdline-bootchart.2
                            /usr/share/vboot/bin/make_dev_ssd.sh --set_config /usr/local/cmdline-bootchart --partitions 2
                            << EOF
                        """
                    }
                }
            }
        }
        
        stage('Reboot DUT') {
            steps {
                sh "ssh root@${params.dut_ip} 'reboot'"
                cleanWs()
                sleep(time:30, unit:"SECONDS")
                
                // Wait the DUT rebooting
                retry(20) {
                    timeout(time: 10, unit: "SECONDS") {
                        sh "ping -c 3 ${params.dut_ip}"
                    }
                }
            }
        }
        
        stage('Generate bootchart') {
            steps {
                sh "ssh root@${params.dut_ip} 'cat /proc/cmdline'"
                sleep(time:5, unit:"SECONDS")   //Wait bootchart file completed
                
                sh "scp root@${params.dut_ip}:/var/log/bootchart/boot-*.tgz ."
                sh "python3 ${env.HOME}/tools/bootchart/pybootchartgui.py -o bootchart-${product_name}-${current_dt}.png --verbose --show-all boot-*.tgz"
                sh "rm -f boot-*.tgz"
            }
            
            post {
                success {
                    archiveArtifacts "*.png"
                }
            }
        }
        
    }
    
    post {
        always {
            echo "Restore original cmdline and reboot DUT"
            sh "ssh root@${params.dut_ip} '/usr/share/vboot/bin/make_dev_ssd.sh --set_config /usr/local/cmdline-orig --partitions 2'"
            sh "ssh root@${params.dut_ip} 'rm -f /usr/local/cmdline-orig.2 /usr/local/cmdline-bootchart.2'"
            sh "ssh root@${params.dut_ip} 'rm -rf /var/log/bootchart'"
            sh "ssh root@${params.dut_ip} 'reboot'"
        }
    }
   
}
