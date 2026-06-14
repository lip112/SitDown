# 운영 배포 메모

> 현재 기준: Docker Compose로 PostgreSQL, 백엔드, 프론트엔드, Nginx를 함께 실행한다.

## 1. 목표 구조

외부에는 Nginx만 80번 포트로 공개하고, Nginx가 Docker Compose 내부 서비스로 요청을 전달한다.

```text
client
      -> nginx:80
      -> /api/**          -> backend:8080
      -> /uploads/**      -> backend:8080
      -> /api-docs        -> backend:8080
      -> /api-docs/       -> backend:8080/api-docs
      -> /swagger-ui/**   -> backend:8080
      -> /swagger-ui.html -> backend:8080
      -> /**              -> frontend:3000
```

`backend`, `frontend`는 Docker Compose의 서비스 이름이다. 같은 Compose 네트워크에 있으면 Nginx 설정에서 그대로 호스트명처럼 사용할 수 있다.

## 2. Docker Compose 예시

```yaml
services:
  postgres:
    image: postgres:16
    container_name: sitdown-postgres
    environment:
      POSTGRES_DB: sitdown
      POSTGRES_USER: ${DB_USERNAME}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data
    networks:
      - sitdown
    restart: unless-stopped

  backend:
    build:
      context: ./repos/backend/SitDown
    image: sitdown-backend:latest
    container_name: sitdown-backend
    depends_on:
      - postgres
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DB_URL: jdbc:postgresql://postgres:5432/sitdown
      DB_USERNAME: ${DB_USERNAME}
      DB_PASSWORD: ${DB_PASSWORD}
      JWT_SECRET: ${JWT_SECRET}
      CORS_ALLOWED_ORIGINS: ${CORS_ALLOWED_ORIGINS}
      REDIS_HOST: 127.0.0.1
      REDIS_PORT: 6379
      MANAGEMENT_HEALTH_REDIS_ENABLED: false
      APP_UPLOAD_DIR: /app/uploads
    expose:
      - "8080"
    volumes:
      - uploads_data:/app/uploads
    networks:
      - sitdown
    restart: unless-stopped

  frontend:
    build:
      context: ./repos/admin/SitDown-admin
      args:
        VITE_API_BASE_URL: ${FRONTEND_API_BASE_URL}
    image: sitdown-admin:latest
    container_name: sitdown-admin
    depends_on:
      - backend
    expose:
      - "3000"
    networks:
      - sitdown
    restart: unless-stopped

  nginx:
    image: nginx:1.27-alpine
    container_name: sitdown-nginx
    depends_on:
      - frontend
      - backend
    ports:
      - "80:80"
    volumes:
      - ./nginx.conf:/etc/nginx/conf.d/default.conf:ro
    networks:
      - sitdown
    restart: unless-stopped

volumes:
  postgres_data:
  uploads_data:

networks:
  sitdown:
```

운영에서는 `frontend`의 `80:3000`, `backend`의 `8080:8080` 포트 매핑을 제거하고 Nginx만 외부에 공개하는 구성이 단순하다. 임시 점검을 위해 백엔드 8080을 열 수는 있지만, 정상 운영 경로는 `http://sitdown.bond/api/**`로 통일한다.

## 3. Nginx 설정

`nginx.conf`는 `docker-compose.yml`과 같은 디렉터리에 둔다. Compose의 volume 설정이 이 파일을 컨테이너 내부 `/etc/nginx/conf.d/default.conf`로 마운트한다.

```nginx
server {
    listen 80;
    server_name sitdown.bond www.sitdown.bond;

    client_max_body_size 5m;

    location /api/ {
        proxy_pass http://backend:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location = /api-docs {
        proxy_pass http://backend:8080/api-docs;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location = /api-docs/ {
        proxy_pass http://backend:8080/api-docs;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /api-docs/ {
        proxy_pass http://backend:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /swagger-ui/ {
        proxy_pass http://backend:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location = /swagger-ui.html {
        proxy_pass http://backend:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /uploads/ {
        proxy_pass http://backend:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location / {
        proxy_pass http://frontend:3000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

`client_max_body_size 5m;`는 모바일 프로필 이미지 업로드가 Nginx 기본 제한인 1MB에서 막히지 않게 한다. `/api-docs`와 `/api-docs/`를 모두 처리하는 이유는 브라우저나 Swagger UI 캐시가 trailing slash가 붙은 주소를 요청할 수 있기 때문이다. 백엔드 springdoc의 실제 문서 경로는 `/api-docs`다.

업로드 파일은 `/uploads/**` 경로로 공개 조회된다. Nginx가 이 경로를 프론트엔드로 보내면 `200 OK`여도 HTML이 내려와 이미지가 깨지므로, `location /uploads/`는 `location /`보다 위에 둔다. 백엔드 컨테이너 재생성 후에도 파일이 유지되도록 `APP_UPLOAD_DIR`와 `uploads_data` 볼륨을 함께 설정한다.

## 4. 환경변수

`application.yml`의 CORS 설정은 아래처럼 환경변수 우선 구조다.

```yaml
allowed-origins: ${CORS_ALLOWED_ORIGINS:...}
```

따라서 Docker Compose에서 `CORS_ALLOWED_ORIGINS`를 넘기면 `application.yml` 기본값은 사용되지 않는다. 운영 `.env`에는 실제 프론트 도메인을 포함한다.

```env
FRONTEND_API_BASE_URL=http://sitdown.bond
CORS_ALLOWED_ORIGINS=http://sitdown.bond,http://www.sitdown.bond,https://sitdown.bond,https://www.sitdown.bond
```

HTTPS를 적용하면 `FRONTEND_API_BASE_URL`도 `https://sitdown.bond`로 맞춘다. HTTPS 페이지에서 HTTP API를 호출하면 브라우저가 mixed content로 차단할 수 있다.

## 5. 배포와 재시작

```bash
docker compose --env-file .env up -d --build
```

Nginx 설정만 바꾼 경우:

```bash
docker exec sitdown-nginx nginx -t
docker exec sitdown-nginx nginx -s reload
```

## 6. 점검 명령

```bash
curl -i http://sitdown.bond/api/health
curl -i http://sitdown.bond/uploads/profiles/{userId}/{filename}
curl -i http://sitdown.bond/api-docs
curl -i http://sitdown.bond/api-docs/
curl -i http://sitdown.bond/swagger-ui/index.html
```

기대 결과:

| URL | 기대 상태 |
|---|---|
| `/api/health` | `200`, JSON |
| `/uploads/profiles/{userId}/{filename}` | `200`, 이미지 파일 또는 파일이 없으면 `404` |
| `/api-docs` | `200`, OpenAPI JSON |
| `/api-docs/` | `200`, OpenAPI JSON |
| `/swagger-ui/index.html` | `200`, Swagger UI HTML |

CORS preflight 확인:

```bash
curl -i -X OPTIONS http://sitdown.bond/api/auth/login \
  -H 'Origin: http://sitdown.bond' \
  -H 'Access-Control-Request-Method: POST' \
  -H 'Access-Control-Request-Headers: content-type'
```

허용된 origin이면 `Access-Control-Allow-Origin` 헤더가 응답에 포함된다.

## 7. 자주 난 문제

### `/api-docs`는 200인데 Swagger 화면은 계속 404를 표시

브라우저가 예전 `301 /api-docs -> /api-docs/` 응답을 disk cache에서 쓰는 경우가 있다.

조치:

1. 개발자도구 Network 탭에서 Disable cache 체크
2. `Ctrl + F5`로 강력 새로고침
3. 그래도 남으면 사이트 데이터에서 `sitdown.bond` 캐시 삭제

### CORS를 추가했는데 403 Invalid CORS request

서버의 `CORS_ALLOWED_ORIGINS` 환경변수가 `application.yml` 기본값을 덮어쓰는지 확인한다.

```bash
docker inspect sitdown-backend | grep -A2 CORS_ALLOWED_ORIGINS
```

### `http://sitdown.bond/swagger-ui/index.html`이 프론트 화면을 반환

80번 포트가 Nginx가 아니라 프론트 컨테이너에 직접 매핑된 상태다. `frontend`의 `80:3000` 매핑을 제거하고 Nginx만 `80:80`을 사용하게 한다.

### 업로드 이미지가 깨져 보임

응답이 이미지인지 먼저 확인한다.

```bash
curl -s -D /tmp/headers.txt -o /tmp/profile.jpg http://sitdown.bond/uploads/profiles/{userId}/{filename}
cat /tmp/headers.txt
file /tmp/profile.jpg
```

`file` 결과가 `HTML document text`이면 Nginx가 `/uploads/`를 프론트엔드로 보내고 있는 것이다. `docker exec sitdown-nginx nginx -T | grep -A12 "location /uploads"`로 실제 적용 설정을 확인하고, 설정 변경 후 `nginx -t`, `nginx -s reload`를 실행한다.
