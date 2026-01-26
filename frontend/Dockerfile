# 1. React 빌드 단계
FROM node:18-alpine as build
WORKDIR /app
COPY package*.json .
RUN npm install
COPY . .
RUN npm run build

# 2. Nginx 실행 단계
FROM nginx:alpine
COPY --from=build /app/build /usr/share/nginx/html
# React Router 새로고침 문제 해결을 위한 설정 (필요시 주석 해제 후 nginx.conf 추가)
# COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]