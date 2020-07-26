<?php

class Operation {
    private $con;

    function __construct($long_query = false) {
        require_once 'connect.php';
        
        $db = new Connect();

        $this->con = $db->connect($long_query);
    }


    function pushID($token, $id, $response) {
        mysqli_report(MYSQLI_REPORT_ALL ^ MYSQLI_REPORT_INDEX);

        
        $insert = $this->con->prepare("SET @id = ?;
        SET @token = ?;
        INSERT INTO `id_lookup` (`app_id`, `fcm_id`) VALUES (@id, @token) 
        ON DUPLICATE KEY UPDATE `fcm_id`=@token");
       

        if (!$insert) {
            $response = $this->build_response($response, false, 'Invalid SQL statement', null, 500);
            return $response;
        }

        $insert->bind_param('ss', $id, $token);

        if ($insert->execute()) {
            $response = $this->build_response($response, true, "ID and token inserted/updated successfully", null, 200);
            return $response;
        }

        $response = $this->build_response($response, false, 'An unknown error occurred', null, 500);
        return $response;
    }

    function getToken($id, $response) {

        $stmt = $this->con->prepare("SELECT fcm_id FROM id_lookup WHERE app_id=?");
        if (!$stmt) {
            return $this->build_response($response, false, 'An error occurred while retrieving data', null, 500);
        }
        $stmt->prepare('s', $id);
        
        $stmt->execute();
        $stmt->bind_result($token);
        $stmt->fetch();

        $success = true;
        $message = 'Retrieved token successfully';
        $data = $token;
        $code = 200;
        $response = $this->build_response($response, $success, $message, $data, $code);
        return $response;
    }

    

    function build_response($response, $success, $message, $data=null, $response_code=200) {
        $response['success'] = $success;
        $response['message'] = $message;
        $response['data'] = $data;
        http_response_code($response_code);
        return $response;
    }
}