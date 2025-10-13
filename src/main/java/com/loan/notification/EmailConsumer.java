package com.loan.notification;

import java.time.Year;
import java.util.Map;
import java.util.function.Consumer;

import javax.mail.internet.MimeMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

import com.loan.ApplicationSubmitEvent;
import com.loan.model.Applicant;
import com.loan.model.LoanApplication;
import com.solacesystems.jcsmp.Topic;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;

/**
 * Notification service that subscribes to events and produces emails
 */
@Service
public class EmailConsumer {

	private static final Logger logger = LoggerFactory.getLogger(EmailConsumer.class);
	private final JavaMailSender mailSender;
	@Value("${mail.username}")
	private String userName;

	public EmailConsumer(JavaMailSender mailSender) {
		this.mailSender = mailSender;
	}

	/**
	 * listens to loan submit event
	 * 
	 * @return
	 */
	@Bean
	public Consumer<ApplicationSubmitEvent> loanSubmit() {
		return data -> {
			try {
				LoanApplication loan = data.getLoanApplication();
				Applicant applicant = loan.getApplicant();

				logger.info("Received event from topic={} payload={}");
				MimeMessage message = mailSender.createMimeMessage();
				MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
				String htmlTemplate = """
						<!DOCTYPE html>
						<html lang="en">
						<head>
						  <meta charset="UTF-8">
						  <title>Loan Application Confirmation</title>
						</head>
						<body style="font-family:Arial, Helvetica, sans-serif; background-color:#f6f9fc; color:#1f2937; margin:0; padding:20px;">
						  <div style="max-width:500px; margin:auto; background:#ffffff; border-radius:10px; padding:24px; box-shadow:0 2px 8px rgba(0,0,0,0.08);">
						    <h2 style="color:#2563eb; margin-top:0;">Loan Application Received</h2>
						    <p>Hi <strong>${applicantName}</strong>,</p>
						    <p>Thank you for submitting your loan application. Here are the details:</p>
						    <div style="background:#f3f4f6; border-radius:8px; padding:16px; margin:16px 0;">
						      <p style="margin:6px 0;"><strong>Loan ID:</strong> ${loanId}</p>
						      <p style="margin:6px 0;"><strong>Amount:</strong> ${currencySymbol}${amount}</p>
						    </div>
						    <p>We’ll review your application and update you shortly.</p>
						    <p style="font-size:12px; color:#6b7280; margin-top:24px;">© ${year} ${companyName}</p>
						  </div>
						</body>
						</html>
						""";
				String htmlBody = htmlTemplate
						.replace("${applicantName}", applicant.getFirstName() + " " + applicant.getLastName())
						.replace("${loanId}", loan.getLoanId()).replace("${currencySymbol}", "$")
						.replace("${amount}", String.format("%.2f", loan.getLoanAmount()))
						.replace("${year}", String.valueOf(Year.now().getValue()))
						.replace("${companyName}", "Loan Company");
				// SimpleMailMessage email = new SimpleMailMessage();
				helper.setTo(applicant.getEmail());
				helper.setSubject("Loan Application Confirmation");
				helper.setText(htmlBody, true);
				helper.setFrom(applicant.getEmail());

				try {
					mailSender.send(message);
					logger.info("Email sent to {}", applicant.getEmail());
				} catch (MailException ex) {
					logger.error("Failed to send email", ex);
				}

				System.out.println("Sent!");
				logger.info("Email sent to {}", applicant.getEmail());
			} catch (Exception ex) {
				logger.error("Failed to send email via Graph", ex);
			}
		};
	}

	/**
	 * listens to document fail event
	 * 
	 * @return
	 */
	@Bean
	public Consumer<Message<Map<String, Object>>> documentFailed() {
		return data -> {
			try {

				logger.info("Received event from topic={documentFailed} payload={}");

				Object payload = data.getPayload();
				String contentText = null;
				String loanId = null;

				String destination = getDestinationName(data);
				if (destination != null) {
					// Extract loanId from topic: replyTopic/{loanId}
					loanId = getLastSegment(destination);
				}
				if (payload instanceof Map) {
					@SuppressWarnings("unchecked")
					Map<String, Object> map = (Map<String, Object>) payload;
					Object content = map.get("content");

					contentText = (content != null) ? content.toString() : null;
				} else {

					contentText = String.valueOf(payload);
				}

				MimeMessage message = mailSender.createMimeMessage();
				MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
				Parser parser = Parser.builder().build();
				HtmlRenderer renderer = HtmlRenderer.builder().build();

				String htmlFromMarkdown = renderer.render(parser.parse(contentText));
				String htmlTemplate = """
						<!DOCTYPE html>
						<html lang="en">
						<head>
						  <meta charset="UTF-8">
						  <title>Document Verification Failed</title>
						</head>
						<body style="font-family: Arial, Helvetica, sans-serif; background-color: #f6f9fc; color: #1f2937; margin: 0; padding: 20px;">
						  <div style="max-width: 600px; margin: auto; background: #ffffff; border-radius: 10px; padding: 24px; box-shadow: 0 2px 8px rgba(0,0,0,0.08);">
						    <h2 style="color: #dc2626; margin-top: 0;">Document Verification Failed - Loan #${loanId}</h2>

						    <p style="font-size: 15px;">The following issues were detected during validation:</p>

						    <div style="background: #f9fafb; border: 1px solid #e5e7eb; border-radius: 8px; padding: 16px;">
						      ${htmlContent}  <!-- here goes rendered Markdown -->
						    </div>

						    <p style="font-size: 12px; color: #6b7280; margin-top: 24px;">© ${year} ${companyName}</p>
						  </div>
						</body>
						</html>
						""";
				String htmlBody = htmlTemplate

						.replace("${loanId}", loanId).replace("${htmlContent}", htmlFromMarkdown)
						.replace("${year}", String.valueOf(Year.now().getValue()))
						.replace("${companyName}", "Loan Company");

				helper.setTo(userName);
				helper.setSubject("Document Verification Failed");
				helper.setText(htmlBody, true);
				helper.setFrom(userName);

				try {
					mailSender.send(message);
					logger.info("Email sent to {}");
				} catch (MailException ex) {
					logger.error("Failed to send email", ex);
				}

				System.out.println("Sent!");

			} catch (Exception ex) {
				logger.error("Failed to send email via Graph", ex);
			}
		};
	}
	/**
	 * listens to document verified event
	 * 
	 * @return
	 */
	@Bean
	public Consumer<Message<Map<String, Object>>> documentVerified() {
		return data -> {
			try {

				logger.info("Received event from topic={documentFailed} payload={}");

				Object payload = data.getPayload();
				String contentText = null;
				String loanId = null;

				String destination = getDestinationName(data);
				if (destination != null) {
					// Extract loanId from topic: replyTopic/{loanId}
					loanId = getLastSegment(destination);
				}
				if (payload instanceof Map) {
					@SuppressWarnings("unchecked")
					Map<String, Object> map = (Map<String, Object>) payload;
					Object content = map.get("content");

					contentText = (content != null) ? content.toString() : null;
				} else {

					contentText = String.valueOf(payload);
				}

				MimeMessage message = mailSender.createMimeMessage();
				MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
				Parser parser = Parser.builder().build();
				HtmlRenderer renderer = HtmlRenderer.builder().build();

				String htmlFromMarkdown = renderer.render(parser.parse(contentText));
				String htmlTemplate = """
						<!DOCTYPE html>
						<html lang="en">
						<head>
						  <meta charset="UTF-8">
						  <title>Document Verification Passed</title>
						</head>
						<body style="font-family: Arial, Helvetica, sans-serif; background-color: #f6f9fc; color: #1f2937; margin: 0; padding: 20px;">
						  <div style="max-width: 600px; margin: auto; background: #ffffff; border-radius: 10px; padding: 24px; box-shadow: 0 2px 8px rgba(0,0,0,0.08);">
						    <h2 style="color: #16a34a; margin-top: 0;">Document Verification Passed - Loan #${loanId}</h2>

						    
						    <div style="background: #f9fafb; border: 1px solid #e5e7eb; border-radius: 8px; padding: 16px;">
						      ${htmlContent}  <!-- here goes rendered Markdown -->
						    </div>

						    <p style="font-size: 12px; color: #6b7280; margin-top: 24px;">© ${year} ${companyName}</p>
						  </div>
						</body>
						</html>
						""";
				String htmlBody = htmlTemplate

						.replace("${loanId}", loanId).replace("${htmlContent}", htmlFromMarkdown)
						.replace("${year}", String.valueOf(Year.now().getValue()))
						.replace("${companyName}", "Loan Company");

				helper.setTo(userName);
				helper.setSubject("Document Verification Passed");
				helper.setText(htmlBody, true);
				helper.setFrom(userName);

				try {
					mailSender.send(message);
					logger.info("Email sent to {}");
				} catch (MailException ex) {
					logger.error("Failed to send email", ex);
				}

				System.out.println("Sent!");

			} catch (Exception ex) {
				logger.error("Failed to send email via Graph", ex);
			}
		};
	}

	private static String getDestinationName(Message<?> msg) {
		Object val = msg.getHeaders().get("solace_destination");
		if (val instanceof String)
			return (String) val;
		if (val instanceof Topic)
			return ((Topic) val).getName();
		return val != null ? val.toString() : null;
	}

	public static String getLastSegment(String path) {
		if (path == null || path.isEmpty()) {
			return null;
		}
		int lastSlash = path.lastIndexOf('/');
		if (lastSlash == -1 || lastSlash == path.length() - 1) {
			return path; // no slash or slash at the end
		}
		return path.substring(lastSlash + 1);
	}
}