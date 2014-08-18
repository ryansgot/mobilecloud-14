/*
 * 
 * Copyright 2014 Jules White
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */
package org.magnum.dataup;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.magnum.dataup.model.Video;
import org.magnum.dataup.model.VideoStatus;
import org.magnum.dataup.model.VideoStatus.VideoState;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

@Controller
public class VideoSvc {
	
	private Map<Long,Video> videos = new HashMap<Long, Video>();
	private AtomicLong currentId = new AtomicLong(1L);
	private VideoFileManager vfm;

	@RequestMapping(value=VideoSvcApi.VIDEO_SVC_PATH, method=RequestMethod.GET)
	public @ResponseBody Collection<Video> getVideoList() {
		System.out.println("[getVideoList] called");
		return videos.values();
	}

	@RequestMapping(value=VideoSvcApi.VIDEO_SVC_PATH, method=RequestMethod.POST)
	public @ResponseBody Video addVideo(@RequestBody Video v) {
		System.out.println("[addVideo] called");
		checkAndSetId(v);
		v.setDataUrl(getDataUrl(v.getId()));
		videos.put(v.getId(), v);
		return v;
	}
	
	@RequestMapping(value=VideoSvcApi.VIDEO_DATA_PATH, method=RequestMethod.POST)
	public @ResponseBody VideoStatus setVideoData(
			@PathVariable(VideoSvcApi.ID_PARAMETER) long id, 
			@RequestParam(VideoSvcApi.DATA_PARAMETER) MultipartFile videoData, 
			HttpServletResponse response) {
		System.out.println("[setVideoData] called: id = " + id);
		if (!videos.containsKey(new Long(id))) {
			System.out.println("[setVideoData] videos DID NOT contain key: " + id + ". Sending 404 response ...");
			response.setStatus(HttpStatus.NOT_FOUND.value());
			return new VideoStatus(VideoState.READY);
		}
		System.out.println("[setVideoData] videos contained key: " + id + ". Saving ...");
		try {
			saveVideoData(videos.get(id), videoData);
		} catch (IOException ioe) {
			System.out.println("[setVideoData] IOException was thrown while saving data for video id: " + id);
			response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
			return new VideoStatus(VideoState.READY);
		}
		System.out.println("[setVideoData] Saved video data for id: " + id);
		response.setStatus(HttpStatus.OK.value());
		return new VideoStatus(VideoState.READY);
	}
	
	@RequestMapping(value=VideoSvcApi.VIDEO_DATA_PATH, method=RequestMethod.GET)
	public @ResponseBody void getVideoData(@PathVariable(VideoSvcApi.ID_PARAMETER) long id, 
													 HttpServletResponse response) {
		System.out.println("[getVideoData] called: id = " + id);
		if (!videos.containsKey(new Long(id))) {
			System.out.println("[getVideoData] videos DID NOT contain key: " + id + ". Sending 404 response ...");
			response.setStatus(HttpStatus.NOT_FOUND.value());
			return;
		}
		System.out.println("[getVideoData] videos contained key: " + id + ". Returning video data ...");
		try {
			serveVideoData(videos.get(id), response);
		} catch (IOException ioe) {
			System.out.println("[getVideoData] IOException occurred while serving video " + id);
			response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
		}
		System.out.println("[getVideoData] Served video data for id: " + id);
		response.setStatus(HttpStatus.OK.value());
	}
	
	// PRIVATE METHODS
	
    private String getDataUrl(long videoId){
        String url = getUrlBaseForLocalServer() + "/video/" + videoId + "/data";
        return url;
    }
    
    private String getUrlBaseForLocalServer() {
    	HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
    	String base = "http://" + request.getServerName() + ((request.getServerPort() != 80) ? ":" + request.getServerPort() : "");
    	return base;
    }
    
	private void checkAndSetId(Video entity) {
		if(entity.getId() == 0){
			entity.setId(currentId.incrementAndGet());
		}
	}
	
    private void saveVideoData(Video v, MultipartFile videoData) throws IOException {
    	if (null == vfm) {
    		vfm = VideoFileManager.get();
    	}
        vfm.saveVideoData(v, videoData.getInputStream());
   }
    
   private void serveVideoData(Video v, HttpServletResponse response) throws IOException {
	   if (null == vfm) {
		   vfm = VideoFileManager.get();
	   }
	   vfm.copyVideoData(v, response.getOutputStream());
   }
}
