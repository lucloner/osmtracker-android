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

    if (check_file_access('/etc/phpmyadmin/config-db.php')) {
        require('/etc/phpmyadmin/config-db.php');
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
    $sqlcmd="INSERT INTO `osmtracker-android`(`序号`, `设备标识`, `名字`, `开始时间`, `追踪组`, `记录时间`, `经度`, `纬度`, `屏幕状态`, `WIFI名称`, `基站信息`, `CreatorInfo`) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    $stmt=$conn->prepare($sqlcmd);
    $stmt->bind_param('ssssssssssss', $c1, $c2, $c3, $c4, $c5, $c6, $c7, $c8, $c9, $c10, $c11, $c12);

//     echo $poststr;
    
    $c1='' . $poststr['序号'];
    $c2='' . $poststr['设备标识'];
    $c3='' . $poststr['名字'];
    $c4='' . $poststr['开始时间'];
    $c5='' . $poststr['追踪组'];
    $c6='' . $poststr['记录时间'];
    $c7='' . $poststr['经度'];
    $c8='' . $poststr['纬度'];
    $c9='' . $poststr['屏幕状态'];
    $c10='' . $poststr['WIFI名称'];
    $c11='' . $poststr['基站信息'];
    $c12='' . mb_substr(json_encode($result),0,1023);
    $result['sqlcmd']=$sqlcmd;
    $result['conn']=$conn;    
    
//     echo $c1 . $c2 . $c3 . $c4 . $c5 . $c6 . $c7 . $c8 . $c9 . $c10 . $c11;

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
