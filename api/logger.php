<?php

require_once dirname(__FILE__) . '/config.php';

class Logger {

    private static $logger;
    private $log_file;
    private $newline;

    private function __construct()
    {
        $this->log_file = fopen(log_path, "a");
        $this->newline = true;
    }

    private function __destruct()
    {
        fclose($this->log_file);
    }

    static function getInstance(): Logger {
        if (!isset(Logger::$logger)) {
            Logger::$logger = new Logger();
        }
        return Logger::$logger;
    }

    function log(string $data, bool $linefeed = true, ?int $length = null) {
        if ($this->newline) fwrite($this->log_file, ((string) date('Y-m-d H:i:s')) .  " IP: {$_SERVER['REMOTE_ADDR']}");
        fwrite($this->log_file, $data, $length);
        if ($linefeed) fwrite($this->log_file, "\n");
        $this->newline = $linefeed;
        $this->maybeShrink();
    }

    private function maybeShrink() {
        if (file_exists(log_path) && filesize(log_path) > 500000) { 
            $shrink_file = fopen(log_path, 'r');
            $temp_log = fopen(temp_log_path, 'a');
            fseek($shrink_file, 100000);
            while ($line = fgets($shrink_file)) {
                fwrite($temp_log, $line);
            }
            fclose($shrink_file);
            fclose($temp_log);
            unlink(log_path); //delete the large log file
            rename(temp_log_path, log_path); //rename the temp file to be the new log file
        }
    }

}