def product_name
def current_dt


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
        
        stage('Run sleep-graph') {
            steps {
                echo 'Run sleep graph'
                cleanWs()
                script {
                    if (params.call_graph) {
                        sh "ssh root@${params.dut_ip} -t 'cd /usr/local/pm-graph/ && ./sleepgraph.py -config config/chromeos-s0ix-callgraph.cfg -o tmp/s0ix-${product_name}-${current_dt}/'"
                    } else {
                        sh "ssh root@${params.dut_ip} -t 'cd /usr/local/pm-graph/ && ./sleepgraph.py -config config/chromeos-s0ix.cfg -o tmp/s0ix-${product_name}-${current_dt}/'"
                    }
                }
            }
        }
        
        stage('Get sleep-graph') {
            steps {
                echo 'Get the report'
                sh "ssh root@${params.dut_ip} 'cd /usr/local/pm-graph/tmp && zip -r s0ix-${product_name}-${current_dt}.zip s0ix-${product_name}-${current_dt}'"
                sh "scp root@${params.dut_ip}:/usr/local/pm-graph/tmp/s0ix-${product_name}-${current_dt}.zip ."
            }
            
            post {
                always {
                    sh "ssh root@${params.dut_ip} 'rm -rf /usr/local/pm-graph && rm /usr/local/pm-graph.zip'"
                }
                success {
                    sh "unzip s0ix-${product_name}-${current_dt}.zip"
                    archiveArtifacts "s0ix-${product_name}-${current_dt}.zip"
                    archiveArtifacts "s0ix-${product_name}-${current_dt}/*"
                    publishHTML([allowMissing: false, alwaysLinkToLastBuild: true, keepAll: true, reportDir: "s0ix-${product_name}-${current_dt}", reportFiles: 'localhost_freeze.html', reportName: 'HTML Report', reportTitles: 'Sleep Graph Report'])
                }
            }
        }
    }
}
