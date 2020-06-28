FROM clojure
COPY . /usr/src/app
WORKDIR /usr/src/app
EXPOSE 3030
CMD ["lein", "ring server-headless"]