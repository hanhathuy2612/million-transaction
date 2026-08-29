FROM mysql:8.0

ARG MYSQL_CNF=mysql-master.cnf
COPY ${MYSQL_CNF} /etc/mysql/conf.d/mysql.cnf
RUN chmod 644 /etc/mysql/conf.d/mysql.cnf
