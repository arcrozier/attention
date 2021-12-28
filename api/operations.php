<?php

//require_once('/home2/stehekin/vendor/firebase/php-jwt/src/JWT.php');
//require_once('/home2/stehekin/vendor/google-api-php-client/google-api-php-client/src/Google/Client.php');
require __DIR__ . '/vendor/autoload.php';

use Firebase\JWT\JWT;

class Operation
{
    private $con;
    private $auth;

    function __construct($long_query = false)
    {
        require_once 'connect.php';

        $db = new Connect();

        $this->con = $db->connect($long_query);
        $this->auth = json_decode(file_get_contents("/home2/stehekin/bin/attention-auth-key.json"), true);
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

    private function createCustomToken()
    {
        $client_email = $this->auth['client_email'];
        $private_key = $this->auth['private_key'];
        $now_seconds = time();
        $payload = array(
            "iss" => $client_email,
            "sub" => $client_email,
            "aud" => "https://identitytoolkit.googleapis.com/google.identity.identitytoolkit.v1.IdentityToolkit",
            "iat" => $now_seconds,
            "exp" => $now_seconds + (3600),
            "uid" => "attention web app"
        );
        return JWT::encode($payload, $private_key, "RS256");
    }

    private function getOauthToken()
    {
        $client = new Google_Client();
        try {
            $client->setAuthConfig("/home2/stehekin/bin/attention-auth-key.json");
            $client->addScope(Google_Service_FirebaseCloudMessaging::CLOUD_PLATFORM);

            $savedTokenJson = $this->readFile();

            if ($savedTokenJson != null) {
                $client->setAccessToken($savedTokenJson);

                if ($client->isAccessTokenExpired()) {
                    $accessToken = $this->generateToken($client);
                    $client->setAccessToken($accessToken);
                }
            } else {
                $accessToken = $this->generateToken($client);
                $client->setAccessToken($accessToken);
            }

            $oauthToken = $accessToken['access_token'];
            return $oauthToken;
        } catch (Google_Exception $e) {
            return "An exception occurred";
        }
    }

    function sendAlert($to, $from, $message, $response)
    {
        $token_response = $this->getToken($to, $response);
        if (!$token_response['success']) {
            return $this->build_response($response, false, "Destination token not found", null, 400);
        }
        $token = $token_response['data'];
        $url = 'https://fcm.googleapis.com/v1/projects/attention-b923d/messages:send';
        $data = array(
            'message' => array(
                'token' => $token,
                //'name' => strval(time()),
                'data' => array(
                    'alert_to' => $to,
                    'alert_from' => $from,
                    'alert_message' => $message
                ),
                'android' => array(
                    'priority' => 'HIGH'
                )
            )
        );
        $json_data = json_encode($data);
        $FCMToken = $this->getOauthToken();
        $headers = array(
            'Content-Type:application/json',
            "Authorization: Bearer " . $FCMToken
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
        if (strpos($result, "error") !== FALSE) {
            return $this->build_response($response, false, "An error occurred while sending message", $result, 500);
        }
        curl_close($ch);
        return $this->build_response($response, true, 'Sent message successfully' . $result, $token);
    }

    function build_response($response, $success, $message, $data = null, $response_code = 200)
    {
        $response['success'] = $success;
        $response['message'] = $message;
        $response['data'] = $data;
        http_response_code($response_code);
        return $response;
    }

    private function readFile()
    {
        $saved_token = file_get_contents("/home2/stehekin/bin/saved_token");
        if ($saved_token === FALSE) return null;
        return $saved_token;
    }

    private function saveFile($file)
    {
        file_put_contents("/home2/stehekin/bin/saved_token", $file);
    }

    private function generateToken($client)
    {
        $client->fetchAccessTokenWithAssertion();
        $accessToken = $client->getAccessToken();

        $tokenJson = json_encode($accessToken);
        $this->saveFile($accessToken);

        return $accessToken;
    }
}
