import boto3
import os
from dotenv import load_dotenv
from boto3.dynamodb.conditions import Key

load_dotenv()

dynamodb = boto3.resource(
    'dynamodb',
    region_name=os.getenv('AWS_REGION'),
    aws_access_key_id=os.getenv('AWS_ACCESS_KEY_ID'),
    aws_secret_access_key=os.getenv('AWS_SECRET_ACCESS_KEY')
)


def get_table(table_name: str):
    return dynamodb.Table(table_name)


def save_item(table_name: str, item: dict):
    table = get_table(table_name)
    table.put_item(Item=item)


def get_items_by_user_date(table_name: str, user_date: str):
    table = get_table(table_name)
    response = table.query(
        KeyConditionExpression=Key('user_date').eq(user_date)
    )
    return response.get('Items', [])


def get_items_by_user(table_name: str, user_id: str, date: str):
    user_date = f"{user_id}#{date}"
    return get_items_by_user_date(table_name, user_date)
