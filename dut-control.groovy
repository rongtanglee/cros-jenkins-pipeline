pipeline {
    agent {
        label 'jenkins-master'
    }
    
    environment {
        PATH="${env.HOME}/bin/hdctools/scripts:${env.PATH}"
    }
    
    parameters {
        string defaultValue: '', description: 'controls, ex: cpu_uart_pty ec_uart_pty gsc_uart_pty', name: 'controls'
        booleanParam description: 'show the value only (default: False)', name: 'value_only'
        booleanParam description: 'show verbose info about controls (default: False)', name: 'verbose'
        string defaultValue: '1', description: 'repeat requested command multiple times (default: 1)', name: 'repeat'
        string defaultValue: '', description: 'repeat requested command for this many seconds', name: 'time'
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
        
        stage('Run dut-control') {
            steps {
                script {
                    def controls_args = ''
                    if (params.value_only) {
                        controls_args += '-o '
                    }
                    
                    if (params.verbose) {
                        controls_args += '--verbose '
                    }
                    
                    if (params.repeat.toInteger() > 1) {
                        controls_args += "-r ${params.repeat} "
                    }
                    if (params.time) {
                        def time_in_sec = params.time.toInteger()
                        controls_args += "-t ${time_in_sec}"
                    }
                    echo "control_args=${controls_args}"
                    
                    def output = sh returnStdout: true, script: "dut-control -- ${controls_args} ${params.controls}"
                    println(output)
                }
            }
        }
    }
}
