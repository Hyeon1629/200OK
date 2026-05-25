import * as cdk from 'aws-cdk-lib';
import { Construct } from 'constructs';
import * as apigwv2 from 'aws-cdk-lib/aws-apigatewayv2';
import { HttpUrlIntegration } from 'aws-cdk-lib/aws-apigatewayv2-integrations';
import { HttpJwtAuthorizer } from 'aws-cdk-lib/aws-apigatewayv2-authorizers';

export class InfraStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props?: cdk.StackProps) {
    super(scope, id, props);

    const albUrl     = process.env.ALB_URL!;
    const userPoolId = process.env.COGNITO_USER_POOL_ID!;
    const clientId   = process.env.COGNITO_CLIENT_ID!;

    if (!albUrl)     throw new Error('ALB_URL 환경변수가 필요합니다.');
    if (!userPoolId) throw new Error('COGNITO_USER_POOL_ID 환경변수가 필요합니다.');
    if (!clientId)   throw new Error('COGNITO_CLIENT_ID 환경변수가 필요합니다.');

    // 기존 HTTP API 참조 (새로 생성하지 않음)
    const api = apigwv2.HttpApi.fromHttpApiAttributes(this, 'CheckdangApi', {
      httpApiId: 'ltrxttl1e8',
    });

    // Cognito JWT Authorizer (HTTP API v2 방식)
    const issuer = `https://cognito-idp.ap-northeast-2.amazonaws.com/${userPoolId}`;
    const authorizer = new HttpJwtAuthorizer('CognitoAuthorizer', issuer, {
      jwtAudience: [clientId],
      authorizerName: 'checkdang-cognito-authorizer',
      identitySource: ['$request.header.Authorization'],
    });

    // ── 공개 경로 (인증 불필요) ──────────────────────────────────

    // /actuator/health
    new apigwv2.HttpRoute(this, 'HealthRoute', {
      httpApi: api,
      routeKey: apigwv2.HttpRouteKey.with('/actuator/health', apigwv2.HttpMethod.GET),
      integration: new HttpUrlIntegration('HealthIntegration', `${albUrl}/actuator/health`),
      authorizer: new apigwv2.HttpNoneAuthorizer(),
    });

    // /api/auth/{proxy+}
    new apigwv2.HttpRoute(this, 'AuthRoute', {
      httpApi: api,
      routeKey: apigwv2.HttpRouteKey.with('/api/auth/{proxy+}', apigwv2.HttpMethod.ANY),
      integration: new HttpUrlIntegration('AuthIntegration', `${albUrl}/api/auth/{proxy}`),
      authorizer: new apigwv2.HttpNoneAuthorizer(),
    });

    // /api/payment/kakao/cancel
    new apigwv2.HttpRoute(this, 'KakaoCancelRoute', {
      httpApi: api,
      routeKey: apigwv2.HttpRouteKey.with('/api/payment/kakao/cancel', apigwv2.HttpMethod.POST),
      integration: new HttpUrlIntegration('KakaoCancelIntegration', `${albUrl}/api/payment/kakao/cancel`),
      authorizer: new apigwv2.HttpNoneAuthorizer(),
    });

    // /api/payment/kakao/fail
    new apigwv2.HttpRoute(this, 'KakaoFailRoute', {
      httpApi: api,
      routeKey: apigwv2.HttpRouteKey.with('/api/payment/kakao/fail', apigwv2.HttpMethod.POST),
      integration: new HttpUrlIntegration('KakaoFailIntegration', `${albUrl}/api/payment/kakao/fail`),
      authorizer: new apigwv2.HttpNoneAuthorizer(),
    });

    // /api/payment/google/rtdn
    new apigwv2.HttpRoute(this, 'GoogleRtdnRoute', {
      httpApi: api,
      routeKey: apigwv2.HttpRouteKey.with('/api/payment/google/rtdn', apigwv2.HttpMethod.POST),
      integration: new HttpUrlIntegration('GoogleRtdnIntegration', `${albUrl}/api/payment/google/rtdn`),
      authorizer: new apigwv2.HttpNoneAuthorizer(),
    });

    // ── 보호 경로 (Cognito JWT 필수) ─────────────────────────────

    // /api/{proxy+} — 나머지 모든 API
    new apigwv2.HttpRoute(this, 'ProtectedRoute', {
      httpApi: api,
      routeKey: apigwv2.HttpRouteKey.with('/api/{proxy+}', apigwv2.HttpMethod.ANY),
      integration: new HttpUrlIntegration('ProtectedIntegration', `${albUrl}/api/{proxy}`),
      authorizer,
    });

    // 기존 ANY / 라우트는 Console에서 삭제 필요 (CDK에서 기존 리소스 삭제 불가)

    new cdk.CfnOutput(this, 'ApiGatewayUrl', {
      value: `https://ltrxttl1e8.execute-api.ap-northeast-2.amazonaws.com`,
      description: 'API Gateway 엔드포인트 URL',
    });
  }
}
