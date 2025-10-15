## -------Architecture Diagram-------------

https://www.figma.com/board/lUSLTNd6bkNYCwGmHZawGJ/Loan-Processing-Flow-with-Solace?node-id=0-1&t=vbeEO5UROfnxwhhf-1

# Email Notification Service

A Java Spring Boot service to process email notifications to end-users and loan officers

## Features
- Subscribes to loan submission events and document processing events in solace event mesh
- Processes data and create appropriate email notificaitons for appropriate users

## How to run
- Update Configuration

  Open src/main/resources/application.yml.

Change Solace connection variables (host, msgVpn, clientUsername, clientPassword) to match your environment.
Change mail properties to match yours

 - Build the Project

   mvn clean install -DskipTests


 - Run the Application

   mvn spring-boot:run
