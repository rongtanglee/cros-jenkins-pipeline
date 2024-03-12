def local_manifest_file


pipeline {
    agent {
        label 'jenkins-master'
    }
    
    parameters {
        string description: 'Chromium OS Branch, ex: release-R79-12607.B, leave empty for ToT local manifest', name: 'branch'
        choice choices: ['ignore.txt', 'ignore-firmware-brya-14505.B.txt', 'ignore-firmware-dedede-13606.B.txt', 'ignore-firmware-hatch-12672.B.txt', 'ignore-firmware-nissa-15217.B.txt', 'ignore-firmware-octopus-11297.B.txt', 'ignore-firmware-puff-13324.B.txt', 'ignore-firmware-volteer-13672.B.txt'], description: 'Projects to be ignored', name: 'ignore_file'
        choice choices: ['ignore_edk2', 'ignore_edk2-firmware-brya-14505.B', 'ignore_edk2-firmware-dedede-13606.B', 'ignore_edk2-firmware-hatch-12672.B', 'ignore_edk2-firmware-nissa-15217.B', 'ignore_edk2-firmware-octopus-11297.B', 'ignore_edk2-firmware-puff-13324.B', 'ignore_edk2-firmware-volteer-13672.B'], description: 'EDK2 projects to be ignored', name: 'ignore_edk2_file'
    }
    
    stages {
        stage('Check parameters') {
            steps {
                echo 'Clone gen_local_manifest'
                echo "branch: ${params.branch}"
                echo "ignore_file: ${params.ignore_file}"
                echo "ignore_edk2_file: ${params.ignore_edk2_file}"
                sh 'python3 --version'
            }
        }
        
        stage('Generate local manifest') {
            steps {
                dir("${env.HOME}/tools/gen_local_manifest") {
                    echo 'Generate local manifest'
                    sh "python3 -m venv gen-local-manifest-venv"
                    
                    script {
                        if (params.branch.toString().isEmpty()) {
                            echo "The branch is empty, get the ToT local manifest"
                            local_manifest_file = 'tot-local.xml'
                            sh """#!/bin/bash
                                source gen-local-manifest-venv/bin/activate
                                python -m pip install -r requirement.txt
                                python3 gen_local_manifest.py -n -f ${params.ignore_file} -e ${params.ignore_edk2_file} -o ${env.WORKSPACE}/${local_manifest_file}
                                deactivate
                            """
                        } else {
                            echo "The branch is ${params.branch}"
                            local_manifest_file = "${params.branch}-local.xml"
                            sh """#!/bin/bash
                                source gen-local-manifest-venv/bin/activate
                                python -m pip install -r requirement.txt
                                python3 gen_local_manifest.py -n -b ${params.branch} -f ${params.ignore_file} -e ${params.ignore_edk2_file} -o ${env.WORKSPACE}/${local_manifest_file}
                                deactivate
                            """
                            
                        }
                    }
                }
            }
        }
    }
    
    post {
        success {
            archiveArtifacts "${local_manifest_file}"
        }
    }
}
