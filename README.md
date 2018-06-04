# image-reader
Lambda function to detect text fragments in images with AWS Rekognition.

## Setup
- S3 bucket
- Dynamo DB table
- Role with access to S3, Rekognition, Dynamo DB

## Run
- Upload an image to S3
- Query tags from Dynamo DB