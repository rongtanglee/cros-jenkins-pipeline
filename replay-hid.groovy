def product_name
def abort_recording = false

pipeline {
    agent {
        label 'jenkins-master'
    }
    
    parameters {
        string defaultValue: '192.168.1.102', description: 'DUT IP address', name: 'dut_ip'
        //base64File description: 'HID event file to be replayed', name: 'hid_file'
        stashedFile description: 'Upload HID Event File', name: 'hid_file'
        string defaultValue: '1', description: 'Replay Iteration', name: 'replay_iter'
        string defaultValue: '3', description: 'Delay between Iterations', name: 'delay'
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
        
        stage('Push tools to DUT') {
            steps {
                
                dir("${env.HOME}/tools") {
                    echo 'Push record-replay-hid tools to DUT'
                    sh "scp record-replay-hid.zip root@${params.dut_ip}:/usr/local/"
                    sh "ssh root@${params.dut_ip} 'cd /usr/local && unzip -o record-replay-hid.zip'"
                }
            }
        }
        
        stage('Push HID file') {
            steps {
                cleanWs()
                script {
                    def buildCause = currentBuild.getBuildCauses()[0]['shortDescription']
                    println ("Cause: " + buildCause)

                    if (buildCause.contains('upstream')) {
                        echo 'Build was triggered by upstream'

                        def upstreamProject = buildCause.substring(buildCause.indexOf('"') + 1, buildCause.lastIndexOf('"'))
                        echo "upstreamProject: ${upstreamProject}"
                        copyArtifacts filter: '*.hid', projectName: "${upstreamProject}", selector: upstream()
                        sh "scp *.hid root@${params.dut_ip}:/usr/local/record-replay-hid/replay.hid"
                    } else {
                        echo 'Build was not trigged by upstream'

                        unstash 'hid_file'
                        sh "scp hid_file root@${params.dut_ip}:/usr/local/record-replay-hid/replay.hid"
                    }
                }
            }
        }
        
        stage('Replay HID file') {
            steps {
                echo "Replay HID file: replay.hid"
                sh "ssh root@${params.dut_ip} 'cd /usr/local/record-replay-hid/ && ./replay-hid.py replay.hid ${params.replay_iter} ${params.delay}'"
            }
            
            post {
                aborted {
                    echo "Stopping the replay-hid"
                    sh "ssh root@${params.dut_ip} 'killall hid-replay'"
                }
            }
        }
    }
}
