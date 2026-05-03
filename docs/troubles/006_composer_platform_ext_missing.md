## 問題
`phpoffice/phpspreadsheet ^3.0` を composer.json に追加し、`composer update` を実行したところ、以下のエラーが連続して発生しインストールできなかった。

```
phpoffice/phpspreadsheet requires ext-gd * -> it is missing from your system.
phpoffice/phpspreadsheet requires ext-zip * -> it is missing from your system.
```

## 原因
ローカル環境（XAMPP）のPHPで `ext-gd` と `ext-zip` が無効になっていた。

ComposerはデフォルトでローカルのPHP環境を「本番と同じ」とみなして依存解決を行う。そのためローカルで無効な拡張が要求されると、lock ファイルの更新自体が失敗する。

## 解決策
`--ignore-platform-req` フラグでローカルの拡張不足を無視してlock更新を行う。

```powershell
composer update phpoffice/phpspreadsheet --no-interaction --ignore-platform-req=ext-gd --ignore-platform-req=ext-zip
```

あわせて、実行環境（DockerとCI）側でも拡張を有効にする対応が必要。

**Dockerfile（amazia-console）**
```dockerfile
RUN apk add --no-cache \
        libpng-dev \
        libjpeg-turbo-dev \
        freetype-dev \
        libzip-dev \
        ... \
    && docker-php-ext-configure gd --with-freetype --with-jpeg \
    && docker-php-ext-install ... zip gd
```

**deploy.yml（test-consoleジョブ）**
```yaml
- uses: shivammathur/setup-php@v2
  with:
    php-version: '8.2'
    extensions: mbstring, pdo, sqlite3, gd, zip
```

## 結果
lock ファイルが更新され、CI・Dockerともに拡張が有効な状態でインストールが通るようになった。

## 補足
- `--ignore-platform-req` はlock更新時のみの措置。実際の実行環境（Docker・CI）では拡張を正しく有効にする必要がある
- XAMPPでext-gdを有効にするには `php.ini` の `;extension=gd` のコメントを外す。ただし今回はローカルでPHPを直接動かす必要がないため対応不要
- 複数の拡張が不足している場合、エラーは1つずつしか出ない。`--ignore-platform-reqs`（複数形・sあり）で全拡張を一括無視することもできる
