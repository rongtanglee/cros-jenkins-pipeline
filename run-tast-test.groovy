def product_name
def build_agent
def tast_cmd
def resultFolder
def buildOutputFile

pipeline {
    agent any

    parameters {
        string defaultValue: '192.168.1.102', description: 'DUT IP address', name: 'dut_ip'
        string defaultValue: 'storage.FullQualificationStress.stress', description: 'Specify test item', name: 'test_item'
        string defaultValue: 'tast_disk_size_gb=128', description: 'Specify runtime variables', name: 'var'
        booleanParam defaultValue: true, description: 'Set verbose output', name: 'verbose'
    }
    
    stages {
        stage('Check DUT') {
            steps {
                sh 'printenv'
                echo 'Check DUT'
                sh "ssh root@${params.dut_ip} 'crossystem'"
                
                script {
                    product_name = sh(script: "ssh root@${params.dut_ip} 'dmidecode -s baseboard-product-name'", returnStdout: true).trim().toLowerCase()
                    def (_m, _f) = sh(script: "ssh root@${params.dut_ip} 'dmidecode -s system-family'", returnStdout: true).trim().toLowerCase().split('_')
                    def family_name = _f
                    
                    echo "product_name: ${product_name}"
                    echo "family_name: ${family_name}"
                    
                    if (family_name == 'brya' || family_name == 'brask') {
                        build_agent = 'cros-brya-d12'
                    } else {
                        build_agent = "cros-${family_name}-d12"
                    }
                    
                    echo "build_agent=${build_agent}"
                    echo "verbose=${params.verbose}"
                }
            }
        }
        
        stage ('Run tast') {
            agent {
                label "${build_agent}"
            }
            
            steps {
                sh 'printenv'
                cleanWs()
                script {
                    if (params.verbose) {
                        tast_cmd = "tast -verbose run "
                    } else {
                        tast_cmd = "tast run "
                    }
                    
                    if (params.var.isEmpty()) {
                        tast_cmd = "${tast_cmd}" + "${params.dut_ip} ${params.test_item}"
                    } else {
                        tast_cmd = "${tast_cmd}" + "-var ${params.var} ${params.dut_ip} ${params.test_item}"
                    }
                    
                    echo "tast_cmd=${tast_cmd}"
                    
                    dir("${env.WORKSPACE}") {
                        buildOutputFile = "${env.WORKSPACE}/build_output.log"
                        sh "touch ${buildOutputFile}"
                    }
                    
                    dir("${env.HOME}/cros-tot") {
                        sh returnStdout: false, script: "${env.HOME}/bin/depot_tools/cros_sdk ${tast_cmd} | tee " + "${buildOutputFile}"
                        def savedOutput = readFile(buildOutputFile).trim()
                        //println(savedOutput)
                        
                        def resultPattern = savedOutput =~ /Results saved to \/tmp\/tast\/results\/(\d{8}-\d{6})/
                        resultFolder = resultPattern[0][1]
                        echo "resultFolder=${resultFolder}"
                    }
                }
            }

            post {
                success {
                    dir("${env.HOME}/cros-tot/out/tmp/tast/results/${resultFolder}") {
                        sh "tar -zcvpf ${env.WORKSPACE}/${product_name}-${resultFolder}.tar.gz *"
                        archiveArtifacts artifacts: 'full.txt', followSymlinks: false
                    }
                    archiveArtifacts artifacts: "${product_name}-${resultFolder}.tar.gz", followSymlinks: false
                }
            }
        }
        
        stage ('Check test result') {
            agent {
                label "${build_agent}"
            }

            steps {
                sh 'printenv'
                script {
                    def Result = sh returnStatus: true, script: "grep -q \'${params.test_item} \\[ PASS \\]\' ${buildOutputFile}"
                    if (Result == 0) {
                        echo "The test ${params.test_item} is PASSED"
                        env.TEST_RESULT = 'TEST_OK'
                    } else {
                        echo "The test ${params.test_item} is FAILED"
                        env.TEST_RESULT = 'TEST_FAIL'
                    }
                    echo "TEST_RESULT=${env.TEST_RESULT}"
                }
            }
        }
    }

    post {
        failure {
            script {
                env.TEST_RESULT = 'BUILD_FAIL'
            }
        }
        aborted {
            script {
                env.TEST_RESULT = 'BUILD_ABORT'
            }
        }
    }
}
