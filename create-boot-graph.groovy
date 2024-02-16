def product_name = 'unknown'
def current_dt = ''

pipeline {
    agent {
        label 'jenkins-master'
    }
    
    parameters {
        string defaultValue: '192.168.1.102', description: 'DUT IP address', name: 'dut_ip'
        booleanParam defaultValue: false, description: 'generate call graph', name: 'call_graph'
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
        
        stage('Push pm-graph to DUT') {
            steps {
                
                dir("${env.HOME}/tools") {
                    sh 'pwd'
                    sh 'ls -la'
                    echo 'Push pm-graph to DUT'
                    sh "scp pm-graph.zip root@${params.dut_ip}:/usr/local/"
                    sh "ssh root@${params.dut_ip} -t 'cd /usr/local && unzip -o pm-graph.zip'"
                }
                
            }
        }
        
        stage('Run boot-graph') {
            steps {
                echo 'Run sleep graph'
                sh "ssh root@${params.dut_ip} 'cat /proc/cmdline'"
                
                script {
                    if (params.call_graph) {
                        sh "ssh root@${params.dut_ip} 'cd /usr/local/pm-graph/ && ./get-bootgraph-cmdline.sh -verbose -fstat -callgraph -maxdepth 3 -manual > cmdline-bootgraph.2'"
                    } else {
                        sh "ssh root@${params.dut_ip} 'cd /usr/local/pm-graph/ && ./get-bootgraph-cmdline.sh -verbose -fstat -manual > cmdline-bootgraph.2'"
                    }
                    
                    sh "ssh root@${params.dut_ip} 'cat /usr/local/pm-graph/cmdline-bootgraph.2'"
                    sh "ssh root@${params.dut_ip} 'cd /usr/local/pm-graph/ && /usr/share/vboot/bin/make_dev_ssd.sh --set_config cmdline-bootgraph --part 2'"
                }
            }
        }
        
        stage('Reboot DUT') {
            steps {
                sh "ssh root@${params.dut_ip} 'reboot'"
                cleanWs()
                sleep(time:30, unit:"SECONDS")
            }
        }
        
        stage('Create boot-graph') {
            steps {
                // Wait the DUT rebooting
                retry(20) {
                    timeout(time: 10, unit: "SECONDS") {
                        sh "ping -c 3 ${params.dut_ip}"
                    }
                }
                
                script {
                    sh "ssh root@${params.dut_ip} 'cat /proc/cmdline'"
                    
                    if (params.call_graph) {
                        sh "ssh root@${params.dut_ip} 'cd /usr/local/pm-graph/ && ./bootgraph.py -verbose -fstat -callgraph -maxdepth 3 -o result/boot-${product_name}-${current_dt}/'"
                    } else {
                        sh "ssh root@${params.dut_ip} 'cd /usr/local/pm-graph/ && ./bootgraph.py -verbose -fstat -o result/boot-${product_name}-${current_dt}/'"
                    }
                    
                    sh "ssh root@${params.dut_ip} 'cd /usr/local/pm-graph/result/ && zip -r boot-${product_name}-${current_dt}.zip boot-${product_name}-${current_dt}/'"
                    sh "scp root@${params.dut_ip}:/usr/local/pm-graph/result/boot-${product_name}-${current_dt}.zip ."
                    sh "unzip boot-${product_name}-${current_dt}.zip"
                } 
            }
            
            post {
                success {
                    archiveArtifacts "boot-${product_name}-${current_dt}/*"
                    archiveArtifacts "boot-${product_name}-${current_dt}.zip"
                    publishHTML([allowMissing: false, alwaysLinkToLastBuild: true, keepAll: true, reportDir: "boot-${product_name}-${current_dt}", reportFiles: 'localhost_boot.html', reportName: 'HTML Report', reportTitles: 'Boot Graph Report'])            
                }
            }
        }
    }
    
    post {
        always {
            sh "ssh root@${params.dut_ip} '/usr/share/vboot/bin/make_dev_ssd.sh --set_config /usr/local/pm-graph/cmdline-orig --partitions 2'"
            sh "ssh root@${params.dut_ip} 'rm -rf /usr/local/pm-graph && rm /usr/local/pm-graph.zip'"
            sh "ssh root@${params.dut_ip} 'reboot'"
        }
    }
}

