def build_agent
def cros_sdk_folder

pipeline {
    agent none
    
    parameters {
        choice choices: ['brya', 'brask', 'rex', 'nissa', 'dedede'], description: 'Choose Reference Board', name: 'board'
        string defaultValue: 'omnigul', description: 'Type the variant name', name: 'variant'
        string defaultValue: '', description: 'Type the USE flags, ex: fw_debug', name: 'use_flags'
        string defaultValue: 'coreboot depthcharge', description: 'cros workon start <packages>, separated by space, ex: chromeos-config-bsp-private chromeos-config chromeos-zephyr chromeos-ec coreboot depthcharge vboot_reference libpayload chromeos-bsp-nissa-private adln-ucode-firmware-private coreboot-private-files-chipset-adln chromeos-bmpblk', name: 'workon_packages'
        
    }
    
    stages {
        stage('Validate Build Parameters') {
            steps {
                script {
                    if (params.board == 'brya' || params.board == 'brask') {
                        build_agent = 'cros-brya-d12'
                        cros_sdk_folder = "cros-fw-brya"
                    } else {
                        build_agent = "cros-${params.board}-d12"
                        cros_sdk_folder = "cros-fw-${params.board}"
                    }
                    
                    echo "build_agent=${build_agent}"
                    echo "cros_sdk_folder=${cros_sdk_folder}"
                }
            }
        }
        
        stage('Build Boot Image') {

            agent {
                label "${build_agent}"
            }
            
            steps {
                script {
                    dir ("${env.HOME}/${cros_sdk_folder}") {
                        sh 'printenv'
                        sh """
                            /home/ron/bin/depot_tools/cros_sdk --enter <<EOF
                            setup_board --board ${params.board}
                            #emerge-${params.board} chromeos-config-bsp-private chromeos-config
                            cros-workon-${params.board} start ${params.workon_packages}
                            USE=\"${params.use_flags}\" FW_NAME=${params.variant} emerge-${params.board} ${params.workon_packages} chromeos-bootimage
                            <<-EOF
                        """
                    }
                }
            }
            
            post {
                success {
                    dir("${env.HOME}/${cros_sdk_folder}/chroot/build/${params.board}/firmware") {
                        archiveArtifacts artifacts: "image-${params.variant}*.serial.bin", fingerprint: true, followSymlinks: false, onlyIfSuccessful: true
                    }
                }
            }
        }
    }
}
