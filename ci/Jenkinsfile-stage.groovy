// Add this stage after your JMeter stage. N8N_WEBHOOK is a Jenkins "Secret text" credential.
stage('Send results to n8n') {
  steps {
    withCredentials([string(credentialsId: 'N8N_WEBHOOK', variable: 'N8N_WEBHOOK')]) {
      sh 'curl -fsS -X POST "$N8N_WEBHOOK" -H "Content-Type: application/json" -d @run.json'
    }
  }
}
