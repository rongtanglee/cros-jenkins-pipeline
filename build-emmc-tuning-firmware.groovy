def build_agent
def cros_sdk_folder
def fw_branch
def prev_local_branch

pipeline {
    agent none
    
    parameters {
        choice choices: ['nissa', 'dedede'], description: 'Choose Reference Board', name: 'board'
        string defaultValue: 'yaviks', description: 'Type the variant name', name: 'variant'
    }
    
    stages {
        stage('Validate Build Parameters') {
            steps {
                script {
                    if (params.board == 'nissa') {
                        fw_branch = 'remotes/cros/firmware-nissa-15217.B'
                    } else if (params.board == 'dedede') {
                        fw_branch = 'remotes/cros/firmware-dedede-13606.B'
                    }
                    
                    build_agent = "cros-${params.board}-d12"
                    cros_sdk_folder = "cros-fw-${params.board}"
                    
                    echo "build_agent=${build_agent}"
                    echo "cros_sdk_folder=${cros_sdk_folder}"
                }
            }
        }
        
        stage('Apply eMMC tuning patches') {
            agent {
                label "${build_agent}"
            }
            
            steps {
                echo 'Apply eMMC tuning patches on depthcharge'
                script {
                    dir("${env.HOME}/${cros_sdk_folder}/src/platform/depthcharge") {
                        prev_local_branch = sh returnStdout: true, script: 'git rev-parse --abbrev-ref HEAD'
                        echo "prev_local_branch = ${prev_local_branch}"
                        
                        sh """
                            git checkout -b emmc-tuning ${fw_branch}
                            git am /mnt/jenkins/tools/emmc-tuning/for-${params.board}/*.patch
                        """
                    }
                }
            }
            
        }
        
        stage('Build Boot Image') {
            agent {
                label "${build_agent}"
            }
            
            steps {
                build job: 'build-boot-image', parameters: [string(name: 'board', value: "${params.board}"), string(name: 'variant', value: "${params.variant}"), string(name: 'workon_packages', value: 'depthcharge coreboot')]
            }
            
            post {
                always {
                    dir("${env.HOME}/${cros_sdk_folder}/src/platform/depthcharge") {
                        sh """
                            git checkout ${prev_local_branch}
                            git branch -D emmc-tuning
                        """
                    }
                }
                success {
                    dir("${env.HOME}/${cros_sdk_folder}/chroot/build/${params.board}/firmware") {
                        archiveArtifacts artifacts: "image-${params.variant}*.serial.bin", fingerprint: true, followSymlinks: false, onlyIfSuccessful: true
                    }
                }
            }
        }
    }
}
