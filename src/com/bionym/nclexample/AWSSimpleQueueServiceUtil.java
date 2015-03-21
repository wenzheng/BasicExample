package com.bionym.nclexample;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.GetQueueUrlRequest;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageResult;

public class AWSSimpleQueueServiceUtil {
	private BasicAWSCredentials credentials;
	private AmazonSQS sqs;
	private String simpleQueue = "ECGSensor";
	private String queueUrl;
	private static volatile AWSSimpleQueueServiceUtil awssqsUtil = new AWSSimpleQueueServiceUtil();

	/**
	 * instantiates a AmazonSQSClient
	 * http://docs.aws.amazon.com/AWSJavaSDK/latest
	 * /javadoc/com/amazonaws/services/sqs/AmazonSQSClient.html Currently using
	 * BasicAWSCredentials to pass on the credentials. For SQS you need to set
	 * your regions endpoint for sqs.
	 */
	private AWSSimpleQueueServiceUtil() {
		try {
			this.credentials = new BasicAWSCredentials("AKIAJEQWUXDSVNMYBAVA",
					"B9dLFy0VNkOnPdKXfcRK78GsFz07KePsYMxnX2Ed");

			this.sqs = new AmazonSQSClient(this.credentials);
			/**
			 * My queue is in singapore region which has following endpoint for
			 * sqs https://sqs.ap-southeast-1.amazonaws.com you can find your
			 * endpoints here
			 * http://docs.aws.amazon.com/general/latest/gr/rande.html
			 *
			 * Overrides the default endpoint for this client
			 * ("sqs.us-east-1.amazonaws.com")
			 */
			this.sqs.setEndpoint("https://sqs.ap-southeast-1.amazonaws.com");
			/*
			 * You can use this in your web app where AwsCredentials.properties
			 * is stored in web-inf/classes
			 */
			// AmazonSQS sqs = new AmazonSQSClient(new
			// ClasspathPropertiesFileCredentialsProvider());
			
			queueUrl = getQueueUrl(simpleQueue);

		} catch (Exception e) {
			System.out.println("exception while creating awss3client : " + e);
		}
	}
	
	 
    public static AWSSimpleQueueServiceUtil getInstance(){
        return awssqsUtil;
    }
    
    public String getQueueName(){
        return awssqsUtil.simpleQueue;
   }
    
    
    /**
     * send a single message to your sqs queue
     * @param queueUrl
     * @param message
     */
    public void sendMessageToQueue(String message){
        SendMessageResult messageResult =  this.sqs.sendMessage(new SendMessageRequest(queueUrl, message));
        System.out.println(messageResult.toString());
    }
    
    /**
     * returns the queueurl for for sqs queue if you pass in a name
     * @param queueName
     * @return
     */
    public String getQueueUrl(String queueName){
        GetQueueUrlRequest getQueueUrlRequest = new GetQueueUrlRequest(queueName);
        return this.sqs.getQueueUrl(getQueueUrlRequest).getQueueUrl();
    }
}
