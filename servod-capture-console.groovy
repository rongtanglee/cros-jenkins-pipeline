def cpu_pty = null
def ec_pty = null
def gsc_pty = null

pipeline {
    agent {
        label 'jenkins-master'
    }
    
    environment {
        PATH="${env.HOME}/bin/hdctools/scripts:${env.PATH}"
    }
    
    parameters {
        booleanParam description: "CPU console", name: 'cpu_console'
        booleanParam description: "EC console", name: 'ec_console'
        booleanParam description: "GSC console", name: 'gsc_console'
    }
    
    stages {
        stage('Check servod') {
            steps {
                script {
                    def status = sh returnStatus: true, script: 'servod-ps'
                    if (status == 0) {
                        echo "servod container is running"
                    } else {
                        echo "servod container is not running, please start it first"
                        currentBuild.result = 'FAILURE'
                    }
                }
            }
        }
        
        stage('Check console devices') {
            steps {
                script {
                    cpu_pty = sh returnStdout: true, script: 'dut-control -- -o cpu_uart_pty'
                    echo "cpu_pty=${cpu_pty}"
                    
                    ec_pty = sh returnStdout: true, script: 'dut-control -- -o ec_uart_pty'
                    echo "ec_pty=${ec_pty}"
                    
                    gsc_pty = sh returnStdout: true, script: 'dut-control -- -o gsc_uart_pty'
                    echo "gsc_pty=${gsc_pty}"
                }
                cleanWs()
            }
        }
        
        stage('Capture consoles') {
            parallel {
                stage('Capture CPU console') {
                    when { expression { params.cpu_console }}
                    steps {
                        catchError {
                            sh script: "script -f cpu_console.log -c \"picocom -b 115200 ${cpu_pty}\""
                        }
                    }
                }
                    
                stage('Capture EC console') {
                    when { expression { params.ec_console }}
                    steps {
                        catchError {
                            sh script: "script -f ec_console.log -c \"picocom -b 115200 ${ec_pty}\""
                        }
                    }
                }
                    
                stage('Capture GSC console') {
                    when { expression { params.gsc_console }}
                    steps {
                        catchError {
                            sh script: "script -f gsc_console.log -c \"picocom -b 115200 ${gsc_pty}\""
                        }
                    }
                }
            }
        }
    }
    
    post {
        aborted {
            script {
                echo "stop picocom"
                sh "killall picocom"
            }
        }
        
        always {
            archiveArtifacts "*.log"
        }
        
    }
}
