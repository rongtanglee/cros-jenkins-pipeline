def product_name
def abort_recording = false
def current_dt

pipeline {
    agent {
        label 'jenkins-master'
    }
    
    parameters {
        string defaultValue: '192.168.1.102', description: 'DUT IP address', name: 'dut_ip'
        booleanParam defaultValue: false, description: 'Replay event after recording', name: 'replay_after_recording'
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
                    
                    Date date = new Date()
                    current_dt = date.format('yyyyMMdd-HHmmss')
                    echo "current_dt: ${current_dt}"
                }
            }
        }
        
        stage('Push tools to DUT') {
            steps {
                
                dir("${env.HOME}/tools") {
                    echo 'Push record-replay-hid tools to DUT'
                    sh "scp record-replay-hid.zip root@${params.dut_ip}:/usr/local/"
                    sh "ssh root@${params.dut_ip} 'cd /usr/local && unzip -o record-replay-hid.zip'"
                }
                
            }
        }
        
        stage('Run record-hid') {
            steps {
                catchError(buildResult: 'SUCCESS', stageResult: 'SUCCESS') {
                    echo "Run record-hid"
                    sh "ssh root@${params.dut_ip} 'cd /usr/local/record-replay-hid/ && ./record-hid.sh record-${product_name}-${current_dt}.hid'"
                }
            }
        }
        
        stage ('Recording..') {
            steps {
                input id: 'stop_recording', message: 'The hid events are recording...,\nPress Stop button to stop the recording.', ok: 'Stop'
            }
            
            post {
                always {
                    echo "Stopping record-hid"
                    sh "ssh root@${params.dut_ip} 'killall hid-recorder'"
                }
                
                aborted {
                    echo "Aborting record-hid"
                    script {
                        abort_recording = true
                    }
                }
            }
        }
        
        stage('Replay HID file') {
            when { 
                expression { return (params.replay_after_recording && !abort_recording) } 
            }
            steps {
                echo "Replay HID file: record-${product_name}-${current_dt}.hid"
                input id: 'start_the_replaying', message: 'Press Replay button to start replaying HID events', ok: 'Replay'
                    
                sh "ssh root@${params.dut_ip} 'cd /usr/local/record-replay-hid/ && ./replay-hid.py record-${product_name}-${current_dt}.hid'"
                echo "Complted replay HID file: record-${product_name}-${current_dt}.hid"
            }
        }

        stage('Get HID file') {
            when { 
                expression { return !abort_recording } 
            }
            steps {
                echo 'Get HID file'
                cleanWs()
                dir("${env.WORKSPACE}/${product_name}") {
                    sh "scp root@${params.dut_ip}:/usr/local/record-replay-hid/record-${product_name}-${current_dt}.hid ."
                }
            }
            
            post {
                always {
                    sh "ssh root@${params.dut_ip} 'rm -rf /usr/local/record-replay-hid*'"
                }
                success {
                    archiveArtifacts "${product_name}/record-${product_name}-${current_dt}.hid"
                }
                aborted {
                    archiveArtifacts "${product_name}/record-${product_name}-${current_dt}.hid"
                }
            }
        }
    }
}
