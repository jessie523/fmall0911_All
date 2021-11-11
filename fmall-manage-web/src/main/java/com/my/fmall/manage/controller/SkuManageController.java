package com.my.fmall.manage.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.dubbo.config.annotation.Service;
import com.my.fmall.bean.SkuInfo;
import com.my.fmall.bean.SkuLsInfo;
import com.my.fmall.bean.SpuImage;
import com.my.fmall.bean.SpuSaleAttr;
import com.my.fmall0911.service.ListService;
import com.my.fmall0911.service.ManageService;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * author:zxy
 *
 * @create 2021-09-16 11:27
 */
@RestController
@CrossOrigin
public class SkuManageController {

    @Reference
    private ManageService manageService;

    @Reference
    private ListService listService;

//     http://localhost:8082/spuImageList?spuId=58
    @GetMapping("/spuImageList")
    public List<SpuImage> getSpuImageList(String spuId){
       return  manageService.getSpuImageList(spuId);
    }

    /**
     * 获取销售属性列表（显示销售属性值）
     * @return
     */
//    http://localhost:8082/spuSaleAttrList?spuId=65
    @GetMapping("/spuSaleAttrList")
    public List<SpuSaleAttr> getSpuSaleAttr(String spuId){

        return manageService.getSpuSaleAttr(spuId);
    }


    /**
     * 保存sku
     * @param skuInfo
     * @return
     */
    @PostMapping("/saveSkuInfo")
    public String saveSkuInfo(@RequestBody SkuInfo skuInfo){
        manageService.saveSkuInfo(skuInfo);
        return "ok";
    }

    // 上传一个商品，如果上传批量！
    @RequestMapping("onSale")
    public void onSale(String skuId){
        // 众筹属性不能拷贝！？
        // 创建一个skuLsInfo 对象
        SkuLsInfo skuLsInfo = new SkuLsInfo();
        // 给skuLsInfo 赋值！
        SkuInfo skuInfo = manageService.getSkuInfo(skuId);
        // 属性拷贝！
        BeanUtils.copyProperties(skuInfo,skuLsInfo);
//        try {
//            org.apache.commons.beanutils.BeanUtils.copyProperties(skuLsInfo,skuInfo);
//        } catch (IllegalAccessException e) {
//            e.printStackTrace();
//        } catch (InvocationTargetException e) {
//            e.printStackTrace();
//        }
        listService.saveSkuInfo(skuLsInfo);
    }
}
