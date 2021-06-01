<?php
/*
Template Name: osm
*/

/* other PHP code here */
get_header();
?>

<div class="main-content clear-fix<?php echo esc_attr(ashe_options( 'general_content_width' )) === 'boxed' ? ' boxed-wrapper': ''; ?>" data-sidebar-sticky="<?php echo esc_attr( ashe_options( 'general_sidebar_sticky' )  ); ?>">
	<!-- Main Container -->
	<div class="main-container" style='width: 100%'>
		
		<article id="page-<?php the_ID(); ?>" <?php post_class(); ?> style='overflow-x: auto;'>

			<?php

			if ( have_posts() ) :

			// Loop Start
			while ( have_posts() ) : the_post();

				if ( has_post_thumbnail() ) {
					echo '<div class="post-media">';
						the_post_thumbnail('ashe-full-thumbnail');
					echo '</div>';
				}

				if ( get_the_title() !== '' ) {
					echo '<header class="post-header">';
						echo '<h1 class="page-title">'. get_the_title() .'</h1>';
					echo '</header>';
				}

				echo '<div class="post-content">';
					the_content('');

					// Post Pagination
					$defaults = array(
						'before' => '<p class="single-pagination">'. esc_html__( 'Pages:', 'ashe' ),
						'after' => '</p>'
					);

					wp_link_pages( $defaults );
				echo '</div>';

			endwhile; // Loop End

			endif;

			?>

			<?php
			$osmsubmitvalue='';
			$osmsubmitkey='';
			if ( is_user_logged_in() ) {

				$osmsubmitkey='' . $_POST['osmsubmitkey'];
				$osmsubmitvalue='' . $_POST['osmsubmitvalue'];
										  
				$sql = "SELECT `序号`,`设备标识`,`名字`,`开始时间`,`追踪组`,`记录时间`,`经度`,`纬度`,`屏幕状态`,`WIFI名称`,`基站信息`,`ID`,`CreateTime`,`IpAddr` FROM `osmtracker-android`";
				
				if( !empty($osmsubmitkey) && !empty($osmsubmitvalue) ){
					$sql = $sql . " WHERE UNIX_TIMESTAMP(`$osmsubmitkey`) BETWEEN $osmsubmitvalue ;";
					echo "<BR />按条件[" . $osmsubmitkey . "]在范围[" . $osmsubmitvalue . "]查询<BR />";
				}
				else {
					$sql = $sql . " ORDER BY `ID` DESC LIMIT 100 ;";
					echo '<BR />显示倒序100条记录<BR />';
				}
				$osm_data = $wpdb->get_results ( $sql );
			?>
			
				<link rel="stylesheet" type="text/css" href="/osminc/css/normalize.css" />
				<link rel="stylesheet" type="text/css" href="/osminc/css/default.css">
				<link rel="stylesheet" href="/osminc/css/daterangepicker.css" />
				<script src="/osminc/moment.min.js"></script>
				<script src="/osminc/jquery.daterangepicker.js"></script>
				<script src="/osminc/htmlson.js"></script>
				<script src="/osminc/xlsx.core.min.js"></script>
				<script src="/osminc/FileSaver.min.js"></script>
				<script src="/osminc/tableexport.min.js"></script>
				<!--
					条件查询
				-->
				<p>
					<form method='post' action='.' >
						查询日期类型:
						<select name="osmsubmitkey" value='<?php echo $osmsubmitkey ?>'>
							<option value="开始时间">开始时间</option>
							<option value="记录时间">记录时间</option>
							<option value="CreateTime">CreateTime</option>
						</select>
						&nbsp;
						日期: <input type="text" name="osmsubmitvalue" value='<?php echo $osmsubmitvalue; ?>' />
						<input type='submit' />
					</form>	
					<button type="button" onclick="doit('xlsx');" >导出Excel</button>	
				</p>
			
				<table id="osmdatatable"></table>
				<script>
					var osmdatatable=jQuery('article table')[0];
					if( !osmdatatable ){
						osmdatatable=jQuery('#osmdatatable')[0];
					}
					else{
						jQuery('#osmdatatable').remove();
					}
					osmdatatable.style+='table-layout: auto; width: 100%; overflow: auto; text-overflow: ellipsis; white-space: pre-line;';
					jQuery("input[name='osmsubmitvalue']").dateRangePicker({
						separator : ' AND ',
						autoClose: false,
						format: 'X',
						time: {
							enabled: true
						}
					});
					jQuery(osmdatatable).htmlson({
						data:<?php echo json_encode ( $osm_data ); ?>
					});
					function doit(type, fn, dl) {
						var elt = osmdatatable;
						var wb = XLSX.utils.table_to_book(elt, {sheet:"Sheet JS"});
						return dl ?
							XLSX.write(wb, {bookType:type, bookSST:true, type: 'base64'}) :
							XLSX.writeFile(wb, fn || ('SheetJSTableExport.' + (type || 'xlsx')));
					}
				</script>
			<?php } ?>
		</article>

		<?php get_template_part( 'templates/single/comments', 'area' ); ?>

	</div><!-- .main-container -->
	
</div><!-- .page-content -->
<?php
get_footer();
?>
