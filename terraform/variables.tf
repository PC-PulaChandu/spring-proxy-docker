variable "aws_region" {
  description = "AWS region to deploy into"
  type        = string
  default     = "ap-south-1"
}

variable "instance_type" {
  description = "EC2 instance type"
  type        = string
  default     = "t3.micro"
}

variable "my_ip_cidr" {
  description = "Your public IP in CIDR form, for example 203.0.113.7/32"
  type        = string

  validation {
    condition     = can(cidrhost(var.my_ip_cidr, 0))
    error_message = "my_ip_cidr must be a valid CIDR block such as 203.0.113.7/32."
  }
}

variable "public_key_path" {
  description = "Path to the SSH public key to install on the server"
  type        = string
  default     = "~/.ssh/trace-proxy-tf.pub"
}

variable "repo_url" {
  description = "Git repository the server clones on first boot"
  type        = string
  default     = "https://github.com/PC-PulaChandu/spring-proxy-docker.git"
}