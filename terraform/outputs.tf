output "public_ip" {
  description = "Public IP of the server"
  value       = aws_instance.app.public_ip
}

output "app_url" {
  description = "Base URL of the proxy"
  value       = "http://${aws_instance.app.public_ip}:8080"
}

output "ssh_command" {
  description = "Command to log in to the server"
  value       = "ssh -i ~/.ssh/trace-proxy-tf ubuntu@${aws_instance.app.public_ip}"
}