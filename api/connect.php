<?php

class Connect {
    private $con;

    function __construct() {

    }

    function connect($long_query = false) {
        include_once dirname(__FILE__) . '/config.php';
        if ($long_query) {
            $this->con = mysqli_init();
            $this->con->options(MYSQLI_OPT_READ_TIMEOUT, 1000);
            $this->con->real_connect(host, user, password, database);
        } else {
            $this->con = new mysqli(host, user, password, database);
        }

        if (mysqli_connect_errno()) {
            echo "failed to connect to MySQL: " . mysqli_connect_error();
        }
        return $this->con;
    }
}