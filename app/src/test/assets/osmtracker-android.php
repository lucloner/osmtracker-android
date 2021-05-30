<?php

    global $_SERVER;
    if (getenv('HTTP_CLIENT_IP')) {
        $ip = getenv('HTTP_CLIENT_IP');
    } else if (getenv('HTTP_X_FORWARDED_FOR')) {
        $ip = getenv('HTTP_X_FORWARDED_FOR');
    } else if (getenv('REMOTE_ADDR')) {
        $ip = getenv('REMOTE_ADDR');
    } else {
        $ip = $_SERVER['REMOTE_ADDR'];
    }
    
    $result=array(
        "result"=>-1,
        "client"=>$ip,
        "user agent"=>$_SERVER['HTTP_USER_AGENT']
    );
    
    if (!function_exists('check_file_access')) {
        function check_file_access($path)
        {
            if (is_readable($path)) {
                return true;
            } else {
                error_log(
                    'phpmyadmin: Failed to load ' . $path
                    . ' Check group www-data has read access and open_basedir restrictions.'
                );
                return false;
            }
        }
    }

    if (check_file_access('phpMyAdmin/config-db.php')) {
        require('phpMyAdmin/config-db.php');
    }
    else{
        $result['result']=-255;
        $result['cause']='lost db config file';
        die(json_encode($result));
    }

    // 创建连接
    $conn = new mysqli($dbserver, $dbuser, $dbpass, $dbname);

    // 检测连接
    if ($conn->connect_error) {
        $result['cause']=$conn->connect_error;
        die(json_encode($result));
    }

    $result['result']=0;

    $poststr=file_get_contents('php://input');
    $result['post']=$poststr;
    if( empty($poststr) ){
        $result['result']=-2;
        $result['cause']='post null';
        die(json_encode($result));
    }

    $poststr=json_decode($poststr,true);
    if( ($result && is_object($result)) || (is_array($result) && !empty($result)) ){
        $result['result']=1;
    }
    else{
        $result['result']=-3;
        $result['cause']='post invalid json';
        die(json_encode($result));
    }

    $conn->query("set names utf-8");
    $sqlcmd="INSERT INTO `osmtracker-android`(`序号`, `设备标识`, `名字`, `开始时间`, `追踪组`, `记录时间`, `经度`, `纬度`, `屏幕状态`, `WIFI名称`, `基站信息`, `CreatorInfo`, `IpAddr`) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    $stmt=$conn->prepare($sqlcmd);
    $stmt->bind_param('sssssssssssss', $c1, $c2, $c3, $c4, $c5, $c6, $c7, $c8, $c9, $c10, $c11, $c12, $c13);

//     echo $poststr;

    $c1=mb_substr('' . $poststr['序号'],0,254);
    $c2=mb_substr('' . $poststr['设备标识'],0,254);
    $c3=mb_substr('' . $poststr['名字'],0,254);
    $c4=mb_substr('' . $poststr['开始时间'],0,254);
    $c5=mb_substr('' . $poststr['追踪组'],0,254);
    $c6=mb_substr('' . $poststr['记录时间'],0,254);
    $c7=mb_substr('' . $poststr['经度'],0,254);
    $c8=mb_substr('' . $poststr['纬度'],0,254);
    $c9=mb_substr('' . $poststr['屏幕状态'],0,254);
    $c10=mb_substr('' . $poststr['WIFI名称'],0,254);
    $c11=mb_substr('' . $poststr['基站信息'],0,254);
    $c12=mb_substr('' . json_encode($result),0,1023);
    $c13='' . $ip;
    $result['sqlcmd']=$sqlcmd;
    $result['conn']=$conn;    
    
//     echo $c1 . $c2 . $c3 . $c4 . $c5 . $c6 . $c7 . $c8 . $c9 . $c10 . $c11;

//     验证字符串
    if(!preg_match("#^\d*$#",$c1)){
        $result['result']=11;
        $result['cause']='忽略:【序号】必须为数字';
        die(json_encode($result));
    }

    if(!is_numeric($c7)){
        $c7='0';
    }
    if(!is_numeric($c8)){
        $c8='0';
    }

    function formatDateTime($date, $format = 'Y-m-d H:i:s'){
        if(preg_match("#^\d*$#",$date)){
            $date=date($format,$date);
        }
        $d = DateTime::createFromFormat($format, $date);
        if($d && $d->format($format) === $date){
            return $date;
        }
        return '1970-01-01 00:00:00';
    }

    $c4=formatDateTime($c4);
    $c6=formatDateTime($c6);

    $stmt->bind_result($sqlresult);
    
    if($stmt->execute()){
        $result['result']=2;
//         echo '<br> 执行成功!';
    }else{
        $result['result']=-4;
        $result['cause']='' . $conn->error;
//         echo '<br> 执行失败' . $conn->error;
    }
    
    if(!$stmt->fetch()){ //没有内容
        $result['result']=3;
//         die("No User");
    }else{//有内容，直接输出
//         echo $sqlresult;
        $result['result']=-5;
    }

    $result['sql']=$stmt;
    $result['sqlreturn']=$sqlresult;
    echo json_encode($result);

    //关闭预编译语句
	$stmt->close();

	//关闭连接
    $conn->close();
?>
