<?php

class Operation
{
    private $con;
    private $auth;

    function __construct($long_query = false)
    {
        require_once 'connect.php';

        $db = new Connect();

        $this->con = $db->connect($long_query);
        $this->auth = file_get_contents(__DIR__ . "/auth.txt");
    }


    function pushID($token, $id, $response)
    {
        mysqli_report(MYSQLI_REPORT_ALL ^ MYSQLI_REPORT_INDEX);


        $insert = $this->con->query("INSERT INTO `id_lookup` (`app_id`, `fcm_id`) VALUES (\"$id\", \"$token\") 
        ON DUPLICATE KEY UPDATE `fcm_id`=\"$token\"");


        if (!$insert) {
            $response = $this->build_response($response, false, 'Invalid SQL statement', null, 500);
            return $response;
        }

        if (!$insert) {
            $response = $this->build_response($response, false, 'An unknown error occurred', null, 500);
            return $response;
        }
        $response = $this->build_response($response, true, "ID and token inserted/updated successfully", null, 200);
        return $response;
    }

    function getToken($id, $response)
    {

        $stmt = $this->con->prepare("SELECT fcm_id FROM id_lookup WHERE app_id=?");
        if (!$stmt) {
            return $this->build_response($response, false, 'An error occurred while retrieving data', null, 500);
        }
        $stmt->bind_param('s', $id);

        $stmt->execute();
        $stmt->bind_result($token);
        $stmt->fetch();

        if ($token == '') {

        }

        $success = true;
        $message = 'Retrieved token successfully';
        $data = $token;
        $code = 200;
        $response = $this->build_response($response, $success, $message, $data, $code);
        return $response;
    }

    function sendAlert($to, $from, $message, $response)
    {
        $token_response = $this->getToken($to, $response);
        if (!$token_response['success']) {
            return $this->build_response($response, false, "Destination token not found", null, 400);
        }
        $token = $token_response['data'];
        $url = 'https://fcm.googleapis.com/v1/projects/attention-b923d/messages:send';
        $data = [
            "registration_ids" => $token,
            "data" => [
                "to" => $to,
                "from" => $from,
                "message" => $message
            ]
        ];
        $json_data = json_encode($data);
        $headers = array(
            'Content-Type:application/json',
            "Authorization:key=$this->auth"
        );

        $ch = curl_init();
        curl_setopt($ch, CURLOPT_URL, $url);
        curl_setopt($ch, CURLOPT_POST, true);
        curl_setopt($ch, CURLOPT_HTTPHEADER, $headers);
        curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
        curl_setopt($ch, CURLOPT_SSL_VERIFYHOST, 0);
        curl_setopt($ch, CURLOPT_SSL_VERIFYPEER, false);
        curl_setopt($ch, CURLOPT_POSTFIELDS, $json_data);
        $result = curl_exec($ch);
        if ($result === false) {
            return $this->build_response($response, false, "FCM send error " . curl_error($ch), null, 500);
        }
        curl_close($ch);
        return $this->build_response($response, true, 'Sent message successfully');
    }

    function build_response($response, $success, $message, $data = null, $response_code = 200)
    {
        $response['success'] = $success;
        $response['message'] = $message;
        $response['data'] = $data;
        http_response_code($response_code);
        return $response;
    }
}
