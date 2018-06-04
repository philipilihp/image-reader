package de.philipilihp.aws.lambda.rekognition;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.rekognition.AmazonRekognition;
import com.amazonaws.services.rekognition.AmazonRekognitionClientBuilder;
import com.amazonaws.services.rekognition.model.DetectTextRequest;
import com.amazonaws.services.rekognition.model.DetectTextResult;
import com.amazonaws.services.rekognition.model.Image;
import com.amazonaws.services.rekognition.model.S3Object;
import com.amazonaws.services.rekognition.model.TextDetection;
import com.amazonaws.services.s3.event.S3EventNotification;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AWS Lambda Function that
 * - reads an image from S3
 * - detects text fragments with Rekognition
 * - persists detected text fragements in Dynamo DB.
 */
public class ImageReader implements RequestHandler<S3Event, List<String>> {

    private static final String ENV_REGION = "AWS_REGION";
    private static final String ENV_DYNAMODB = "DYNAMODB_TABLE";

    public List<String> handleRequest(S3Event s3Event, Context context) {

        final LambdaLogger logger = context.getLogger();

        // Get s3 bucket & filename from event:
        S3EventNotification.S3Entity s3Entity = s3Event.getRecords().get(0).getS3();
        String bucketName = s3Entity.getBucket().getName();
        String filename = s3Entity.getObject().getKey();
        logger.log(String.format(
                "Image uploaded: %s/%s ", bucketName, filename));

        // Extract text from image with aws rekognition:
        Regions region = Regions.fromName(System.getenv(ENV_REGION));

        AmazonRekognition rekognitionClient = AmazonRekognitionClientBuilder.standard()
                .withRegion(region)
                .build();

        DetectTextRequest request = new DetectTextRequest().withImage(
                new Image().withS3Object(
                        new S3Object()
                                .withName(filename)
                                .withBucket(bucketName)));

        DetectTextResult result = rekognitionClient.detectText(request);

        // Write text results to Dynamo DB:
        String dynamoDbTable = System.getenv(ENV_DYNAMODB);

        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard()
                .withRegion(region)
                .build();

        List<String> textExtracts = new ArrayList<>();
        for (TextDetection td : result.getTextDetections()) {
            textExtracts.add(td.getDetectedText());

            Map<String, AttributeValue> item = new HashMap<>();
            item.put("id", new AttributeValue(UUID.randomUUID().toString()));
            item.put("image", new AttributeValue(filename));
            item.put("text", new AttributeValue(td.getDetectedText()));

            client.putItem(new PutItemRequest(dynamoDbTable, item));
        }

        logger.log("Detected texts: " + textExtracts);
        return textExtracts;
    }
}
