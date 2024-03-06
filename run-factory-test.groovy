def product_name

pipeline {
    agent {
        label 'jenkins-master'
    }
    
    parameters {
        string defaultValue: '192.168.1.102', description: 'DUT IP address', name: 'dut_ip'
        string defaultValue: 'RunIn.BraskRunInDozingStress', description: 'factory tests item', name: 'test_item'
    }

    stages {
        stage('Check DUT') {
            steps {
                sh 'printenv'
                echo "Check DUT IP: ${params.dut_ip}"
                sh "ssh root@${params.dut_ip} 'crossystem'"
                script {
                    product_name = sh(script: "ssh root@${params.dut_ip} 'dmidecode -s baseboard-product-name'", returnStdout: true).trim().toLowerCase()
                    if (product_name.contains("_")) {
                        def index = product_name.indexOf("_")
                        product_name = product_name.substring(0, index)
                    }
                    echo "product_name: ${product_name}"
                }
            }
        }
        
        stage('Install factory toolkit') {
            steps {
                build job: 'install-factory-toolkit', parameters: [string(name: 'dut_ip', value: "${params.dut_ip}")]
            }
        }
        
        stage('Run factory RunInDozingStress') {
            steps {
                script {
                        def test_id = sh(script: "ssh root@${params.dut_ip} 'factory tests | grep ${params.test_item}'", returnStdout: true).trim()
                        println("test_id: ${test_id}")
                    
                    if ( ! test_id.isEmpty()) {
                        sh "ssh root@${params.dut_ip} 'factory clear'"
                        sh "ssh root@${params.dut_ip} 'factory run main_${product_name}:${params.test_item}'"
                        sh "ssh root@${params.dut_ip} 'factory run-status'"
                        sh "ssh root@${params.dut_ip} 'factory wait'"
                    } else {
                        println("No such test item: ${params.test_item}")
                        currentBuild.result = 'FAILURE'
                    }
                    
                }
            }
            
            post {
                success {
                    sh "ssh root@${params.dut_ip} 'factory run-status'" 
                    script {
                        def result = sh returnStatus: true, script: "ssh root@${params.dut_ip} 'factory run-status | grep -q FAILED'"
                        if (result == 0) {
                            env.TEST_RESULT = 'TEST_FAIL'
                        } else {
                            env.TEST_RESULT = 'TEST_OK'
                        }
                    }
                    echo "TEST_RESULT=${env.TEST_RESULT}"
                    sh "ssh root@${params.dut_ip} 'factory clear'" 
                }
                
                failure {
                    script {
                        sh "ssh root@${params.dut_ip} 'factory clear'"
                        echo "No such test items: ${params.test_item}"
                        env.TEST_RESULT = 'BUILD_FAIL'
                    }
                }
                
                aborted {
                    script {
                        sh "ssh root@${params.dut_ip} 'factory clear'"
                        env.TEST_RESULT = 'BUILD_ABORT'
                    }
                }
            }
        }

    }
}
