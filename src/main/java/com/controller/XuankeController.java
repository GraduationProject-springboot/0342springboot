
package com.controller;

import java.io.File;
import java.math.BigDecimal;
import java.net.URL;
import java.text.SimpleDateFormat;
import com.alibaba.fastjson.JSONObject;
import java.util.*;
import org.springframework.beans.BeanUtils;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.context.ContextLoader;
import javax.servlet.ServletContext;
import com.service.TokenService;
import com.utils.*;
import java.lang.reflect.InvocationTargetException;

import com.service.DictionaryService;
import org.apache.commons.lang3.StringUtils;
import com.annotation.IgnoreAuth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import com.baomidou.mybatisplus.mapper.EntityWrapper;
import com.baomidou.mybatisplus.mapper.Wrapper;
import com.entity.*;
import com.entity.view.*;
import com.service.*;
import com.utils.PageUtils;
import com.utils.R;
import com.alibaba.fastjson.*;

/**
 * 选课信息
 * 后端接口
 * @author
 * @email
*/
@RestController
@Controller
@RequestMapping("/xuanke")
public class XuankeController {
    private static final Logger logger = LoggerFactory.getLogger(XuankeController.class);

    private static final String TABLE_NAME = "xuanke";

    @Autowired
    private XuankeService xuankeService;


    @Autowired
    private TokenService tokenService;

    @Autowired
    private ChengjiService chengjiService;//学生成绩
    @Autowired
    private DictionaryService dictionaryService;//字典
    @Autowired
    private JiaoshiService jiaoshiService;//教师
    @Autowired
    private KechengService kechengService;//课程信息
    @Autowired
    private NewsService newsService;//公告信息
    @Autowired
    private PaikeService paikeService;//排课信息
    @Autowired
    private XuankexianzhiService xuankexianzhiService;//选课限制
    @Autowired
    private YonghuService yonghuService;//学生
    @Autowired
    private UsersService usersService;//管理员


    /**
    * 后端列表
    */
    @RequestMapping("/page")
    public R page(@RequestParam Map<String, Object> params, HttpServletRequest request){
        logger.debug("page方法:,,Controller:{},,params:{}",this.getClass().getName(),JSONObject.toJSONString(params));
        String role = String.valueOf(request.getSession().getAttribute("role"));
        if(false)
            return R.error(511,"永不会进入");
        else if("学生".equals(role))
            params.put("yonghuId",request.getSession().getAttribute("userId"));
        else if("教师".equals(role))
            params.put("jiaoshiId",request.getSession().getAttribute("userId"));
        CommonUtil.checkMap(params);
        PageUtils page = xuankeService.queryPage(params);

        //字典表数据转换
        List<XuankeView> list =(List<XuankeView>)page.getList();
        for(XuankeView c:list){
            //修改对应字典表字段
            dictionaryService.dictionaryConvert(c, request);
        }
        return R.ok().put("data", page);
    }

    /**
    * 后端详情
    */
    @RequestMapping("/info/{id}")
    public R info(@PathVariable("id") Long id, HttpServletRequest request){
        logger.debug("info方法:,,Controller:{},,id:{}",this.getClass().getName(),id);
        XuankeEntity xuanke = xuankeService.selectById(id);
        if(xuanke !=null){
            //entity转view
            XuankeView view = new XuankeView();
            BeanUtils.copyProperties( xuanke , view );//把实体数据重构到view中
            //级联表 学生
            //级联表
            YonghuEntity yonghu = yonghuService.selectById(xuanke.getYonghuId());
            if(yonghu != null){
            BeanUtils.copyProperties( yonghu , view ,new String[]{ "id", "createTime", "insertTime", "updateTime", "yonghuId"});//把级联的数据添加到view中,并排除id和创建时间字段,当前表的级联注册表
            view.setYonghuId(yonghu.getId());
            }
            //级联表 课程信息
            //级联表
            KechengEntity kecheng = kechengService.selectById(xuanke.getKechengId());
            if(kecheng != null){
            BeanUtils.copyProperties( kecheng , view ,new String[]{ "id", "createTime", "insertTime", "updateTime", "yonghuId"});//把级联的数据添加到view中,并排除id和创建时间字段,当前表的级联注册表
            view.setKechengId(kecheng.getId());
            }
            //修改对应字典表字段
            dictionaryService.dictionaryConvert(view, request);
            return R.ok().put("data", view);
        }else {
            return R.error(511,"查不到数据");
        }

    }

    /**
    * 后端保存
    */
    @RequestMapping("/save")
    public R save(@RequestBody XuankeEntity xuanke, HttpServletRequest request){
        logger.debug("save方法:,,Controller:{},,xuanke:{}",this.getClass().getName(),xuanke.toString());

        String role = String.valueOf(request.getSession().getAttribute("role"));
        if(false)
            return R.error(511,"永远不会进入");
        else if("学生".equals(role))
            xuanke.setYonghuId(Integer.valueOf(String.valueOf(request.getSession().getAttribute("userId"))));

        Wrapper<XuankeEntity> queryWrapper = new EntityWrapper<XuankeEntity>()
            .eq("kecheng_id", xuanke.getKechengId())
            .eq("yonghu_id", xuanke.getYonghuId())
            ;

        logger.info("sql语句:"+queryWrapper.getSqlSegment());
        XuankeEntity xuankeEntity = xuankeService.selectOne(queryWrapper);
        if(xuankeEntity==null){
            List<XuankexianzhiEntity> xuankexianzhiEntities = xuankexianzhiService.selectList(null);
            if(xuankexianzhiEntities.size()<0){
                return R.error("选课限制表无数据");
            }
            if(xuankexianzhiEntities.get(0).getKaishiTime().getTime() > new Date().getTime() || new Date().getTime() > xuankexianzhiEntities.get(0).getJieshuTime().getTime()){
                return R.error("不在选课限制时间之内");
            }
            Wrapper<XuankeEntity> xuankeList = new EntityWrapper<XuankeEntity>()
                    .eq("yonghu_id", xuanke.getYonghuId())
                    ;
            List<XuankeEntity> xuankeEntities = xuankeService.selectList(xuankeList);
            Integer xuankeshuliang = xuankeEntities.size()+1;
            if(xuankeshuliang > xuankexianzhiEntities.get(0).getXuankexianzhiNumber()){
                return R.error("当前选("+xuankeEntities.size()+")课,选课限制为:"+xuankexianzhiEntities.get(0).getXuankexianzhiNumber());
            }
            xuanke.setInsertTime(new Date());
            xuanke.setCreateTime(new Date());
            xuankeService.insert(xuanke);
            return R.ok();
        }else {
            return R.error(511,"请不要重复选课");
        }
    }

    /**
    * 后端修改
    */
    @RequestMapping("/update")
    public R update(@RequestBody XuankeEntity xuanke, HttpServletRequest request) throws NoSuchFieldException, ClassNotFoundException, IllegalAccessException, InstantiationException {
        logger.debug("update方法:,,Controller:{},,xuanke:{}",this.getClass().getName(),xuanke.toString());
        XuankeEntity oldXuankeEntity = xuankeService.selectById(xuanke.getId());//查询原先数据

        String role = String.valueOf(request.getSession().getAttribute("role"));

            xuankeService.updateById(xuanke);//根据id更新
            return R.ok();
    }



    /**
    * 删除
    */
    @RequestMapping("/delete")
    public R delete(@RequestBody Integer[] ids, HttpServletRequest request){
        logger.debug("delete:,,Controller:{},,ids:{}",this.getClass().getName(),ids.toString());
        List<XuankeEntity> oldXuankeList =xuankeService.selectBatchIds(Arrays.asList(ids));//要删除的数据
        xuankeService.deleteBatchIds(Arrays.asList(ids));

        return R.ok();
    }


    /**
     * 批量上传
     */
    @RequestMapping("/batchInsert")
    public R save( String fileName, HttpServletRequest request){
        logger.debug("batchInsert方法:,,Controller:{},,fileName:{}",this.getClass().getName(),fileName);
        Integer yonghuId = Integer.valueOf(String.valueOf(request.getSession().getAttribute("userId")));
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        try {
            List<XuankeEntity> xuankeList = new ArrayList<>();//上传的东西
            Map<String, List<String>> seachFields= new HashMap<>();//要查询的字段
            Date date = new Date();
            int lastIndexOf = fileName.lastIndexOf(".");
            if(lastIndexOf == -1){
                return R.error(511,"该文件没有后缀");
            }else{
                String suffix = fileName.substring(lastIndexOf);
                if(!".xls".equals(suffix)){
                    return R.error(511,"只支持后缀为xls的excel文件");
                }else{
                    URL resource = this.getClass().getClassLoader().getResource("static/upload/" + fileName);//获取文件路径
                    File file = new File(resource.getFile());
                    if(!file.exists()){
                        return R.error(511,"找不到上传文件，请联系管理员");
                    }else{
                        List<List<String>> dataList = PoiUtil.poiImport(file.getPath());//读取xls文件
                        dataList.remove(0);//删除第一行，因为第一行是提示
                        for(List<String> data:dataList){
                            //循环
                            XuankeEntity xuankeEntity = new XuankeEntity();
//                            xuankeEntity.setKechengId(Integer.valueOf(data.get(0)));   //课程 要改的
//                            xuankeEntity.setYonghuId(Integer.valueOf(data.get(0)));   //学生 要改的
//                            xuankeEntity.setInsertTime(date);//时间
//                            xuankeEntity.setCreateTime(date);//时间
                            xuankeList.add(xuankeEntity);


                            //把要查询是否重复的字段放入map中
                        }

                        //查询是否重复
                        xuankeService.insertBatch(xuankeList);
                        return R.ok();
                    }
                }
            }
        }catch (Exception e){
            e.printStackTrace();
            return R.error(511,"批量插入数据异常，请联系管理员");
        }
    }




}

