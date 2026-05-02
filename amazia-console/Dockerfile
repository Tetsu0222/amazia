FROM php:8.2-cli-alpine

RUN apk add --no-cache \
        libpng-dev \
        oniguruma-dev \
        libxml2-dev \
        zip \
        unzip \
    && docker-php-ext-install pdo pdo_mysql mbstring

COPY --from=composer:2 /usr/bin/composer /usr/bin/composer

WORKDIR /app
COPY . .

RUN composer install --no-dev --optimize-autoloader --no-interaction

EXPOSE 8000

# 起動時に config:cache を実行（環境変数が揃った状態でキャッシュする）
CMD ["sh", "-c", "php artisan config:cache && php artisan route:cache && php artisan serve --host=0.0.0.0 --port=8000"]
