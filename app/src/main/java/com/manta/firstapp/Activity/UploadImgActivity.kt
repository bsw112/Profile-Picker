package com.manta.firstapp.Activity

import LoadingDialogFragment
import android.Manifest
import android.content.Intent
import android.content.Intent.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.widget.addTextChangedListener
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.android.volley.Response
import com.android.volley.toolbox.Volley
import com.manta.firstapp.Helper.VolleyHelper
import com.manta.firstapp.Adapter.UploadImgAdapter
import com.manta.firstapp.Default.*
import com.manta.firstapp.Helper.GlobalHelper
import com.manta.firstapp.Helper.MyFileHelper
import com.manta.firstapp.R
import com.manta.firstapp.VolleyMultipartRequest
import com.theartofdev.edmodo.cropper.CropImage
import com.theartofdev.edmodo.cropper.CropImageView
import kotlinx.android.synthetic.main.activity_upload_img.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

data class MyBitmap(val imgName: String, val bitmap: Bitmap)

/**
 * 게시물을 수정, 작성하는 액티비티
 * 갤러리에서 이미지를 가져오거나, 카메라로 사진을 찍을 수 있다.
 */
class UploadImgActivity : AppCompatActivity() {

    private val REQUEST_IMAGE_CAPTURE = 1
    private val REQUEST_PICK_FROM_ALBUM = 2
    private val REQUEST_PERMISSIONS = 3
    private lateinit var mUploadImgAdapter: UploadImgAdapter

    //카메라로 찍은 사진을 저장할 절대경로
    lateinit var mCurrentPhotoPath: String

    /**
     * 기존 게시글을 수정하는 중인가?
     */
    private var mIsModify: Boolean = false

    /**
     * 기존 게시글 정보. 게시글을 수정했는지 안했는지 확인하기 위함
     */
    private var mPostInfo: PostInfo = PostInfo()


    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_upload_img)

        //리사이클러뷰 셋팅
        mUploadImgAdapter = UploadImgAdapter(mPostInfo.myPictures, this)
        gv_upload_picture.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        gv_upload_picture.adapter = mUploadImgAdapter

        //카테고리 표시하는 스피너셋팅
        val spinnerAdapter  = ArrayAdapter(this, R.layout.spinner_item, R.id.tv_spinner_item, GlobalHelper.getInstance(this).mCategory)
        sp_upload_category.adapter = spinnerAdapter


        //내 게시물 수정시, 내 게시물 정보를 불러와서 표시해줌.
        val postId = intent.getLongExtra(EXTRA_POSTID, -1)
        if (postId >= 0) {

            val loadingDialog = LoadingDialogFragment()
            loadingDialog.show(supportFragmentManager, null)

            mIsModify = true

            VolleyHelper.getInstance(this).getPostInfo(postId, mPostInfo)
            mPostInfo.mOnInitialized =
                {
                    if(mPostInfo.category >= 0 && mPostInfo.category < GlobalHelper.getInstance(this).mCategory.size)
                        sp_upload_category.setSelection(mPostInfo.category)

                    et_upload_content.setText(mPostInfo.content)
                    et_upload_title.setText(mPostInfo.title)
                    mUploadImgAdapter.notifyDataSetChanged()

                    loadingDialog.dismiss()
                }

        }

        //카메라로 사진추가
        btn_upload_camera.setOnClickListener {
            if (!packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY))
                Toast.makeText(this, "카메라를 사용할 수 없습니다.", Toast.LENGTH_SHORT).show()
            else if (mPostInfo.myPictures.size >= 5)
                Toast.makeText(this, "사진은 5장까지만 등록할 수 있습니다.", Toast.LENGTH_LONG).show()
            else
                dispatchTakePictureIntent()
        }


        //앨범에서 사진추가
        btn_upload_addPicture.setOnClickListener {

            if (mPostInfo.myPictures.size >= 5) {
                Toast.makeText(this, "사진은 5장까지만 등록할 수 있습니다.", Toast.LENGTH_LONG).show()
            } else
                dispatchGalleryIntent()
        }

        //제출
        tv_upload_save.setOnClickListener {

            if (mPostInfo.myPictures.isEmpty()) {
                Toast.makeText(this, "사진을 하나 이상 등록하세요!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (mIsModify)
                uploadPostToServer(getString(R.string.urlToServer) + "updatePost/")
            else
                uploadPostToServer(getString(R.string.urlToServer) + "writePost/")

        }




        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);


        var currTextCnt = if (mIsModify) mPostInfo.title.length else 0
        tv_upload_titleCnt.text = "(${currTextCnt}/ ${MAX_TITLE_LENGTH})"
        currTextCnt = if (mIsModify) mPostInfo.content.length else 0
        tv_upload_contentCnt.text = "(${currTextCnt} / ${MAX_CONTENT_LENGTH})"

        et_upload_title.addTextChangedListener {
            tv_upload_titleCnt.text = "(${it?.length} / ${MAX_TITLE_LENGTH})"
        }

        et_upload_content.addTextChangedListener {
            tv_upload_contentCnt.text = "(${it?.length} / ${MAX_CONTENT_LENGTH})"
        }


    }

    /**
     * by 변성욱
     * 갤러리에서 가져온 이미지 URI, 카메라로 찍은 이미지 URI를 처리해서
     * 비트맵으로 변환한뒤 화면에 표시한다.
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode != RESULT_OK) {
            return;
        }


        //카메라 리퀘스트
        if (requestCode == REQUEST_IMAGE_CAPTURE) {

            MyFileHelper.galleryAddPic(this, mCurrentPhotoPath)

            //저장한 파일 불러와서 추가
            val f = File(mCurrentPhotoPath) // /storage/emulated/0/Android/data/com.example.firstapp/files/Pictures/JPEG_20201021_184543_5177963341725398557.jpg
            val uri = Uri.fromFile(f) // file:///storage/emulated/0/Android/data/com.example.firstapp/files/Pictures/JPEG_20201021_184543_5177963341725398557.jpg
            launchImageCrop(uri)
            // getBitmapFromUri(uri)?.let { mPostInfo.myPictures.add(MyPicture(it, "", "", 0)) }
        } else if (requestCode == REQUEST_PICK_FROM_ALBUM) {
            //이미지크롭 요청
            val uris = getUrisFromData(data)
            for (uri in uris) {
                launchImageCrop(uri)
                // getBitmapFromUri(uri)?.let { mPostInfo.myPictures.add(MyPicture(it, "", "", 0)) }
            }
        } else if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE) {
            var result = CropImage.getActivityResult(data);
            getBitmapFromUri(result.uri)?.let {
                mPostInfo.myPictures.add(MyPicture(it, "", "", 0))
            }
        }

        //픽쳐가 추가되었음을 알리고, 화면을 갱신하라고한다.
        mUploadImgAdapter.notifyDataSetChanged()
    }


    /**
     * by 변성욱
     * 작성한 게시글을 서버에 업로드한다.
     * 멀티파트리퀘스트를 통해 이미지와 게시글정보를 함께 서버로 보낸다.
     */
    private fun uploadPostToServer(url: String) {

        //Loading Dialog
        val loadingDialog = LoadingDialogFragment()

        val volleyMultipartRequest: VolleyMultipartRequest = object : VolleyMultipartRequest(
            Method.POST, url,
            Response.Listener {
                Toast.makeText(applicationContext, "게시물을 등록하였습니다.", Toast.LENGTH_LONG).show()
                loadingDialog.dismiss();
                LocalBroadcastManager.getInstance(this).sendBroadcast(Intent().apply {
                    action = ACTION_NEED_REFRESH;
                })
                finish();

            },
            Response.ErrorListener { error ->
                Toast.makeText(applicationContext, error.message, Toast.LENGTH_LONG).show()
                Log.e("GotError", "" + error.message)
            }) //여기까지가 object가 구현하는 VolleyMultipartRequest
        //여기서부터는 익명클래스 정의부분
        {
            override fun getByteData(): ArrayList<Pair<String, DataPart>> {
                //map이 중복허용안해서 arrayList로 바꿨다.
                val params = ArrayList<Pair<String, DataPart>>()
                //모든 사진을 바이트배열로 변환해서 바디에 쓴다.
                //key는 html form뷰의 name 항목. 즉, 파라미터가 되는듯
                for (picture in mPostInfo.myPictures) {
                    params.add(
                        Pair(
                            "image", DataPart(
                                System.currentTimeMillis().toString(),
                                picture.bitmap?.let { getFileDataFromDrawable(it) }, "image/webp"
                            )
                        )
                    )
                }

                return params
            }

            override fun getParams(): MutableMap<String, String> {
                //map은 중복허용 안함. 주의!
                val params: MutableMap<String, String> = HashMap()
                //게시물 수정인경우 추가로 보낼 정보들
                if (mIsModify) {
                    mPostInfo?.let {
                        params.put("postId", it.postId.toString())
                        params.put("view", it.viewCnt.toString())

                        var likes = arrayListOf<Int>()
                        for (picture in it.myPictures)
                            likes.add(picture.likes)
                        params.put("likes", likes.toString())

                        params.put("warn", it.warn.toString())
                    }
                }
                params.put("content", et_upload_content.text.toString())
                params.put("title", et_upload_title.text.toString())
                params.put("category", sp_upload_category.selectedItemPosition.toString())
                LoginActivity.mAccount?.email?.let {
                    params.put("email", it)
                }
                return params
            }


        }


        //adding the request to volley
        val requestQ = Volley.newRequestQueue(this)
        requestQ.add(volleyMultipartRequest)

        loadingDialog.show(supportFragmentManager, "다이어로그")

    }

    //네트워크로 비트맵을 보내기위해 byteArray로 변환한다.
    fun getFileDataFromDrawable(bitmap: Bitmap): ByteArray? {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.WEBP, 60, byteArrayOutputStream)
        return byteArrayOutputStream.toByteArray()
    }

    //외부저장소 쓰기, 읽기 권한을 요청한 결과를 받는다.
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        var isDenied: Boolean = false;

        //권한이 하나라도 거부되었나 체크
        for (result in grantResults) {
            if (result == PackageManager.PERMISSION_DENIED)
                isDenied = true
        }

        //권한이 거부되었을때
        if (isDenied) {

        } else
            pickPictureFromGallay()

    }

    //이미지 크롭
    private fun launchImageCrop(uri: Uri?) {
        CropImage.activity(uri).setGuidelines(CropImageView.Guidelines.ON)
            .setCropShape(CropImageView.CropShape.RECTANGLE)
            .setAspectRatio(3, 4)
            .setFixAspectRatio(false)
            .setGuidelines(CropImageView.Guidelines.ON)
            .setInitialCropWindowPaddingRatio(0.0F)
            .setScaleType(CropImageView.ScaleType.CENTER_CROP)
            .setAutoZoomEnabled(false)
            .start(this)
    }

    //갤러리에서 이미지를 선택하는 액티비티로 전환한다.
    private fun pickPictureFromGallay() {
        //이미 퍼미션을 받았으면 바로 그냥 갤러리로
        Intent(ACTION_GET_CONTENT).let {
//            it.type = Images.Media.CONTENT_TYPE
            it.type = "image/*"
            it.putExtra(EXTRA_ALLOW_MULTIPLE, true)
            intent.putExtra("crop", true)
            startActivityForResult(createChooser(it, "Select Picture"), REQUEST_PICK_FROM_ALBUM)
        }
    }


    @RequiresApi(Build.VERSION_CODES.M)
    /**
     * by 변성욱
     * 갤러리에서 사진을 선택하기 위해 권한을 요청하고,
     * 이미 권한이 있으면 갤러리에서 이미지를 선택하기 위한 액티비티로 전환한다.
     * 권한을 거부당하면 권한이 필요한 이유를 UI로 보여준다.
     */
    private fun dispatchGalleryIntent() {
        //외부 스토리지 쓰기, 읽기 퍼미션이 있는지 먼저 확인한다.
        if ((ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) &&
            (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
        ) {
            //이미 퍼미션을 받았으면 바로 그냥 갤러리로
            pickPictureFromGallay()

        } else {
            //만약 교육용 UI를 표시해야한다면 (사용자가 퍼미션을 거부한적이 있으면)
            if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE) ||
                shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)
            ) {
                //교육용 UI보여주기
                Toast.makeText(applicationContext, "권한이 필요합니다", Toast.LENGTH_SHORT).show()
            }
            //퍼미션 요청
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE), REQUEST_PERMISSIONS
            )

        }

    }

    /**
     * URI부터 비트맵을 뽑아낸다.
     * 갤러리에서 고른 이미지나 카메라로 찍은 이미지들은
     * 그대로 ImageView를 통해 보여주기에는 해상도가 너무 높은경우가 많으므로
     * 적절히 해상도를 낮춰서 뽑는다.
     */
    private fun getBitmapFromUri(uri: Uri): Bitmap? {
        var bitmap : Bitmap? = null;
        //어차피 다운샘플링시에 1/4 처럼 비율로 줄이니까 비율은 유지됨.
        //대충 이정도 크기로 줄여라라고 하면 됨.
        val requestWidth : Int = 512;
        val requestHeight : Int = 512;

        //The new ImageDecoder api is much more powerful and supports a variety of different types.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(contentResolver, uri)
            //크기를 줄여서 디코딩한다.
            bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, src ->
                decoder.setTargetSampleSize(calculateInSampleSize(info.size.height, info.size.width,requestWidth, requestHeight))
            };
        } else {
            val option: BitmapFactory.Options = BitmapFactory.Options();

            contentResolver.openInputStream(uri)?.use { inputStream ->
                //먼저 비트맵을 조사한다.
                option.inJustDecodeBounds = true
                BitmapFactory.decodeStream(inputStream, null, option)

                //비트맵의 크기를 토대로 샘플링할 사이즈를 구한다.
                option.inSampleSize = calculateInSampleSize(option.outHeight, option.outWidth, requestWidth, requestHeight)
            }

            // BitmapFactory.decodeStream 하면 inputStream이 변형되므로, 옵션을 얻고난다음에는
            // 다시 inputStream을 만들어야한다.
            contentResolver.openInputStream(uri)?.use { inputStream ->
                option.inJustDecodeBounds = false
                //크기를 줄여서 디코딩한다.
                bitmap = BitmapFactory.decodeStream(inputStream, null, option)
            }

        }

        return bitmap;
    }

    //타겟 너비와 높이를 기준으로 2의 거듭제곱인 샘플 크기 값을 계산하는 메서드.
    //왜 2의 거듭제곱인가? 2픽섹을 1.5픽셀로 줄일 수는 없으니까. 3같은 수를 넣어봤자 2로 바꾼단다.
    //According to this inSampleSize just reduce the pixel count. It cant be used on whole numbers,
    // you cant map 2 pixel to 1.5 pixels, thats way it`s power of 2.
    //https://stackoverrun.com/ko/q/10820265
    fun calculateInSampleSize(rawHeight : Int, rawWidth : Int, reqWidth: Int, reqHeight: Int): Int {

        var inSampleSize = 1

        if (rawHeight > reqHeight || rawWidth > reqWidth) {

            val halfHeight: Int = rawHeight / 2
            val halfWidth: Int = rawWidth / 2

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    /**
     * by 변성욱
     * 카메라로 사진을 찍는 액티비티를 실행한다.
     */
    private fun dispatchTakePictureIntent() {

        if(!MyFileHelper.isExternalStorageWritable())
            Toast.makeText(this, "저장공간이 부족해서 카메라앱을 실행시킬 수 없습니다.", Toast.LENGTH_SHORT).show()


        /*
        카메라 기능이 있는 앱을 찾아서 실행시킨다.
        만약, 그런 앱이 없는데  startActivity()를 호출하면 앱이 정지된다.
        따라서 resolveActivity로 그 결과가 null인지 아닌지에 따라서 startActivity를 수행한다.
         */
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            // Ensure that there's a camera activity to handle the intent
            takePictureIntent.resolveActivity(packageManager)?.also {
                // Create the File where the photo should go
                val photoFile: File? = try {
                    MyFileHelper.createImageTempFile(this).apply {
                        mCurrentPhotoPath = absolutePath
                    }

                } catch (ex: IOException) {
                    // Error occurred while creating the File
                    null
                }

                // Continue only if the File was successfully created
                photoFile?.also { file ->
                    //만든 임시파일 절대경로를 콘텐츠스키마로 변환하기.
                    //카메라앱이 쓰려면 콘텐츠스키마로 변경해야하는 것인가?
                    val photoURI: Uri = FileProvider.getUriForFile(
                        this,
                        "com.manta.firstapp.fileprovider",
                        file
                    )

                    //절대경로
                    //storage/emulated/0/Android/data/com.example.firstapp/files/Pictures/JPEG_20201022_125001_910373070437525236.jpg
                    //photoURI
                    //content://com.example.firstapp.fileprovider/my_images/Pictures/JPEG_20201022_125001_910373070437525236.jpg

                    //사진을 찍고 photoURI의 경로를 가진 파일에 write한다.
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)

                    startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
                }
            }
        }
    }


    /**
     * by 성욱
     * onActivityResult의 data로 넘어온 intent를 풀어 이미지의 uri를 뽑아낸다.
     */
    private fun getUrisFromData(data: Intent?): ArrayList<Uri> {

        //하나 이상을 선택할경우 clipData에 uri가  들어가고
        //하나만 선택할경우 data.data에 uri가 들어감.. 왜 그렇게 만들었는지 모르겠음.
        val uriList = ArrayList<Uri>()

        val clipData = data?.clipData

        if (clipData == null)
            data?.data?.let { uriList.add(it) };
        else {
            if (clipData.itemCount > 5)
                Toast.makeText(this, "사진은 5장까지 추가가능합니다.", Toast.LENGTH_SHORT).show()

            for (i in 0 until clipData.itemCount.coerceAtMost(5)) {
                uriList.add(clipData.getItemAt(i).uri)
            }
        }


        return uriList
    }


    override fun onDestroy() {
        //상호참조 끊기
        mUploadImgAdapter.onDestroy()
        super.onDestroy()
    }
}

