pipeline{
    agent any
    environment{
	//environment setup with the Anypoint Credentials(referenced from Global Jenkins credentials) and APP Repository
		APP_REPO='https://gitlab.blr.teksystems.com/amagarwal/devops-test.git'
		ANYPOINT_URI='https://anypoint.mulesoft.com/accounts/login'
        AP_CREDENTIALS=credentials('Anypoint') 
		GROUP_ID='ae5728e7-177f-40ee-a211-f0f4a4157a07'
		ASSET_ID='devops-test'
		EXCHANGE_URI='https://anypoint.mulesoft.com/exchange/api/v1/assets'
		VALUE_ONE = 'Sandbox'
        VALUE_TWO = 'QA'
    }
	parameters {
		choice(name: 'TARGET', choices: ['Sandbox', 'QA'], description: 'Environment to deploy the code')
		string(name: 'VERSION', defaultValue: '1.0.0', description: 'Exchange asset version')
		string(name: 'APP_BRANCH', defaultValue: 'develop', description: 'Branch from which the code is to be checked out')
    }
    stages{
        stage('Declarative: Checkout SCM'){
            steps{
                 echo "Start Checkout SCM"
				 checkout(
					[
						$class: 'GitSCM',
						branches: [[name: "${env.APP_BRANCH}"]],
						doGenerateSubmoduleConfigurations: false,
						extensions: [[$class: 'CleanCheckout']],
						submoduleCfg: [],
						userRemoteConfigs: 
						[
							[
								credentialsId: 'GITLabs_Password', 
								url: "${APP_REPO}"
							]
						]
					])
					echo "Finished Checkout SCM"
            }
        }
		stage ('Prepare Environment') 
		{
	         steps 
			{
				echo "Start Prepare Environment"
			    script 
				{
				    if (("${TARGET}" == 'Sandbox')) 
					{
					env.FILEID = 'devops-dev.properties'
					}
					else if (("${TARGET}" == 'QA')) 
					{
					env.FILEID = 'devops-qa.properties'
					}
			    }
				echo "Prepare Environment Finished"
			}
		}		
		stage('BootStrap target configuration'){
		//injects required properties file at workspace
            steps{  
				echo "Start BootStrap target configuration"
                configFileProvider([configFile(fileId: "${FILEID}", targetLocation: "${WORKSPACE}")]) {}
                configFileProvider([configFile(fileId: "settings.xml",targetLocation: "${WORKSPACE}")]) {}
				echo " Finished BootStrap target configuration"
            }
        }
        stage('Set Version'){
		// sets artifact version in reference to the successful build (BUILD_ID is an environment variable listing build specific number, used to set version)
            steps{    
				script 
				{
					echo "Start Set Version"
					def token = readJSON text: sh(script:"curl -k -d 'username=$AP_CREDENTIALS_USR&amp;password=$AP_CREDENTIALS_PSW' ${ANYPOINT_URI}", returnStdout: true)
					def URI="${EXCHANGE_URI}/${GROUP_ID}/${ASSET_ID}/${VERSION}"
					def status_code = sh(script:"curl  -s -o nul -w '%{http_code}' -k --request GET '${URI}' --header 'Authorization: bearer ${token.access_token}' --header 'Content-Type: application/json' ", returnStdout: true)
					echo "${status_code}"
					if (("${status_code}" == "200")) //checking if the asset version exists in exchange or not?
					{
					    bat "mvn versions:set -DnewVersion=${VERSION} -f pom.xml"      
					}
					else if (("${status_code}" != "200"))
					{
                        error("Build failed. Required GAV not found in exchange")
					}
					echo "Finished Set Version"		
				}
            }
        }
		stage('Maven Deploy to SANDBOX') {
		  when {
			expression {
			   VALUE_ONE == "${TARGET}"
			   }
			}
			steps {
				echo "Start Deploy to CloudHub Sandbox"
 				bat "mvn deploy -Dusername=$AP_CREDENTIALS_USR -Dpassword=$AP_CREDENTIALS_PSW -Dmaven.properties=${FILEID} -DskipTests -DmuleDeploy"
				echo "Finished Deploy to CloudHub Sandbox"
			}
        }
		stage('Maven Deploy to QA') {
		  when {
			expression {
			   VALUE_TWO == "${TARGET}"
			   }
			}
			steps {
				echo "Start Deploy to CloudHub QA"
				bat "mvn deploy -Dusername=$AP_CREDENTIALS_USR -Dpassword=$AP_CREDENTIALS_PSW -Dmaven.properties=${FILEID} -DskipTests -DmuleDeploy"				
				echo "Finished Deploy to CloudHub QA"			
			}				
        }
	}
    post {
        always {
                cleanWs()
        }
    }
}