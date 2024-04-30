def dev_node

pipeline {
    agent {
        label 'jenkins-master'
    }
    
    parameters {
        choice choices: ['rex', 'brya', 'brask', 'nissa', 'dedede'], description: 'Select Platform Board', name: 'board'
        string defaultValue: 'R122', description: 'OS Image Release/Milestone', name: 'release', trim: true
        string defaultValue: '15753.0.0', description: 'OS Image Version/Prefix', name: 'version', trim: true
        string defaultValue: 'PSSD', description: 'USB Key Model Name, use command \"lsblk -o PATH,MODEL\" to model name', name: 'usb_key_model', trim: true
    }

    stages {
        stage('Check USB key') {
            steps {
                script {
                    dev_node = sh(script: "${env.HOME}/tools/scripts/find-usb-key.sh ${params.usb_key_model}", returnStdout:true).trim()
                    echo "dev_node=${dev_node}"

                    if (dev_node.contains('sda')) {
                        echo "Can't flash to /dev/sda"
                        currentBuild.result = 'FAILED'
                    }
                }
            }
        }
        
        stage('Download CrOS Image') {
            steps {
                build job: 'moblab-download-cros-image', parameters: [string(name: 'board', value: "${params.board}"), string(name: 'release', value: "${params.release}"), string(name: 'version', value: "${params.version}")]
                
            }
        }
        
        stage('Flash CrOS Image') {
            steps {
                dir("${env.HOME}/Downloads/${params.release}-${params.version}-${params.board}/") {
                    sh "sudo dd if=chromiumos_test_image.bin of=${dev_node} bs=8M iflag=fullblock oflag=dsync status=progress"
                    sh "sync"
                }
            }
        }
    }
}
